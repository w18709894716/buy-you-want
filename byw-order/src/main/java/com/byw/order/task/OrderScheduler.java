package com.byw.order.task;

import com.byw.common.redis.lock.DistributedLock;
import com.byw.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 订单定时任务：自动确认收货、超时未支付取消的对账兜底。
 *
 * <p>多实例部署下用 Redisson 分布式锁保证同一轮只有一个节点执行（抢不到锁的节点直接跳过）。
 * 采用 Spring @Scheduled 实现，与 byw-settle 的定时任务方案保持一致（项目未引入独立调度中间件）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderScheduler {

    /** 分布式锁 key */
    private static final String LOCK_AUTO_CONFIRM = "byw:lock:task:order-auto-confirm";
    private static final String LOCK_TIMEOUT_CANCEL = "byw:lock:task:order-timeout-cancel";

    /** 抢锁等待时间：0 表示只尝试一次，抢不到立即跳过，不让调度线程堆积 */
    private static final long LOCK_WAIT_SECONDS = 0L;

    private final OrderService orderService;
    private final DistributedLock distributedLock;

    /** 发货后自动确认收货的天数（默认 7 天） */
    @Value("${byw.order.auto-confirm-days:7}")
    private int autoConfirmDays;

    /** 支付超时时间（分钟），与 RocketMQ 延迟等级 16(30 分钟) 保持一致 */
    @Value("${byw.order.pay-timeout-minutes:30}")
    private int payTimeoutMinutes;

    /** 对账宽限（分钟）：延迟消息可能重试，超过 超时时间+宽限 才由兜底任务取消，避免与消费者抢跑 */
    @Value("${byw.order.timeout-cancel-grace-minutes:10}")
    private int timeoutCancelGraceMinutes;

    /** 单轮最多兜底取消多少笔，避免积压时长时间持锁 */
    @Value("${byw.order.timeout-cancel-batch-size:500}")
    private int timeoutCancelBatchSize;

    /**
     * 定时扫描已发货超过 autoConfirmDays 天仍未确认收货的订单，自动确认收货。
     * 幂等：仅处理待收货(2)状态订单，且状态流转为 CAS 更新，重复执行不会影响已完成订单。
     */
    @Scheduled(fixedDelayString = "${byw.order.auto-confirm-scan-interval-ms:3600000}")
    public void autoConfirmReceive() {
        distributedLock.tryExecuteWithLock(LOCK_AUTO_CONFIRM, LOCK_WAIT_SECONDS, TimeUnit.SECONDS, () -> {
            try {
                orderService.autoConfirmReceive(autoConfirmDays);
            } catch (Exception e) {
                log.error("自动确认收货定时任务执行失败", e);
            }
        });
    }

    /**
     * 超时未支付订单的对账兜底：正常应由 ORDER_TIMEOUT_CANCEL 延迟消息取消，
     * 消息丢失或进了死信时订单会永久停留待付款、库存永久预占，此处补齐。
     * 命中即说明消息链路异常，故用 warn 级别暴露。
     */
    @Scheduled(fixedDelayString = "${byw.order.timeout-cancel-scan-interval-ms:900000}")
    public void cancelTimeoutUnpaidOrders() {
        distributedLock.tryExecuteWithLock(LOCK_TIMEOUT_CANCEL, LOCK_WAIT_SECONDS, TimeUnit.SECONDS, () -> {
            try {
                List<String> orderNos = orderService.listTimeoutUnpaidOrderNos(
                        payTimeoutMinutes + timeoutCancelGraceMinutes, timeoutCancelBatchSize);
                if (orderNos.isEmpty()) {
                    return;
                }
                int count = 0;
                for (String orderNo : orderNos) {
                    try {
                        // 经代理调用，复用 cancelOrder 的事务与状态机（库存释放、优惠券释放、状态日志）
                        orderService.cancelOrder(orderNo, "超时未支付，系统自动取消", "系统");
                        count++;
                    } catch (Exception e) {
                        log.error("超时未支付订单兜底取消失败: orderNo={}", orderNo, e);
                    }
                }
                log.warn("超时未支付订单兜底取消完成: 处理订单数={}（正常应由延迟消息取消，命中说明消息链路可能有异常）", count);
            } catch (Exception e) {
                log.error("超时未支付订单对账任务执行失败", e);
            }
        });
    }
}
