package com.byw.order.task;

import com.byw.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 订单定时任务：自动确认收货兜底。
 * 采用 Spring @Scheduled 实现，与 byw-settle 的定时任务方案保持一致（项目未引入独立调度中间件）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderScheduler {

    private final OrderService orderService;

    /** 发货后自动确认收货的天数（默认 7 天） */
    @Value("${byw.order.auto-confirm-days:7}")
    private int autoConfirmDays;

    /**
     * 定时扫描已发货超过 autoConfirmDays 天仍未确认收货的订单，自动确认收货。
     * 幂等：仅处理待收货(2)状态订单，重复执行不会影响已完成订单。
     */
    @Scheduled(fixedDelayString = "${byw.order.auto-confirm-scan-interval-ms:3600000}")
    public void autoConfirmReceive() {
        try {
            orderService.autoConfirmReceive(autoConfirmDays);
        } catch (Exception e) {
            log.error("自动确认收货定时任务执行失败", e);
        }
    }
}
