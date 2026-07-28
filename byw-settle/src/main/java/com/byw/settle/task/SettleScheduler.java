package com.byw.settle.task;

import com.byw.settle.service.SettleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 结算定时任务：扫描已过冷静期（收货 + T+N）的待结算单，转入商家可用余额。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SettleScheduler {

    private final SettleService settleService;

    /**
     * 每 10 分钟扫描一次到期待结算单并入账。
     * 幂等：settleDueRecords 内部按乐观锁更新状态，重复执行不会重复入账。
     */
    @Scheduled(fixedDelayString = "${byw.settle.scan-interval-ms:600000}")
    public void settleDueRecords() {
        try {
            settleService.settleDueRecords();
        } catch (Exception e) {
            log.error("T+N 结算定时任务执行失败", e);
        }
    }
}
