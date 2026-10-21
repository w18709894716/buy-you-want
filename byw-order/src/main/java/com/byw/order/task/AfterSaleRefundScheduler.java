package com.byw.order.task;

import com.byw.common.redis.lock.DistributedLock;
import com.byw.order.service.AfterSaleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 售后退款对账兜底任务。
 *
 * <p>退款链路已拆成"本地事务置退款中 → 事务外远程退款 → 本地事务落终态"，
 * 若最后一步失败（进程崩溃、订单编排抛错），售后单会停在「退款中」而钱已退回。
 * 本任务定期重新驱动，退款按售后单号幂等，重复驱动不会重复退款。
 *
 * <p>与 byw-order 既有 OrderScheduler 一致，采用 Spring @Scheduled（项目未引入调度中间件）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AfterSaleRefundScheduler {

    /** 分布式锁 key：多实例下同一轮只允许一个节点执行 */
    private static final String LOCK_RETRY_REFUND = "byw:lock:task:aftersale-refund-retry";

    private final AfterSaleService afterSaleService;
    private final DistributedLock distributedLock;

    /** 超过该分钟数仍停在「退款中」才重试，避开与正常同步调用的抢跑 */
    @Value("${byw.order.aftersale-refund-stuck-minutes:5}")
    private int stuckMinutes;

    /** 单轮最多重试多少笔，避免积压时长时间持锁 */
    @Value("${byw.order.aftersale-refund-batch-size:200}")
    private int batchSize;

    @Scheduled(fixedDelayString = "${byw.order.aftersale-refund-scan-interval-ms:300000}")
    public void retryStuckRefunds() {
        distributedLock.tryExecuteWithLock(LOCK_RETRY_REFUND, 0, TimeUnit.SECONDS, () -> {
            try {
                List<Long> ids = afterSaleService.listStuckRefundIds(stuckMinutes, batchSize);
                if (ids.isEmpty()) {
                    return;
                }
                int done = 0;
                for (Long id : ids) {
                    try {
                        afterSaleService.driveRefundIfPending(id);
                        done++;
                    } catch (Exception e) {
                        log.error("退款中售后单重试失败: afterSaleId={}", id, e);
                    }
                }
                log.warn("售后退款对账重试完成: 命中={}, 成功={}（命中说明终态回写曾失败，需关注）", ids.size(), done);
            } catch (Exception e) {
                log.error("售后退款对账任务执行失败", e);
            }
        });
    }
}
