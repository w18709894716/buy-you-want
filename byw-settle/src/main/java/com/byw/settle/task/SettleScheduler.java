package com.byw.settle.task;

import com.byw.common.redis.lock.DistributedLock;
import com.byw.settle.service.SettleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 结算定时任务：扫描已过冷静期（收货 + T+N）的待结算单，转入商家可用余额。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SettleScheduler {

    /** 分布式锁 key：多实例下同一轮只允许一个节点执行 */
    private static final String LOCK_SETTLE_DUE = "byw:lock:task:settle-due-records";

    private final SettleService settleService;
    private final DistributedLock distributedLock;

    /**
     * 每 10 分钟扫描一次到期待结算单并入账。
     * 幂等：settleDueRecords 内部按条件更新状态 + 余额原子更新，重复执行不会重复入账；
     * 分布式锁进一步避免多实例同时扫描同一批数据。
     */
    @Scheduled(fixedDelayString = "${byw.settle.scan-interval-ms:600000}")
    public void settleDueRecords() {
        distributedLock.tryExecuteWithLock(LOCK_SETTLE_DUE, 0, TimeUnit.SECONDS, () -> {
            try {
                settleService.settleDueRecords();
            } catch (Exception e) {
                log.error("T+N 结算定时任务执行失败", e);
            }
        });
    }
}
