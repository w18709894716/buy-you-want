package com.byw.order.task;

import com.byw.common.redis.lock.DistributedLock;
import com.byw.order.entity.CompensationTask;
import com.byw.order.mapper.CompensationTaskMapper;
import com.byw.order.service.CompensationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 补偿任务扫描器：把"事务提交后没能执行成功"后置动作重新驱动起来。
 *
 * <p>与 {@link AfterSaleRefundScheduler} 的分工：那个扫的是业务单据的状态（售后单停在「退款中」），
 * 本任务扫的是补偿任务表。两者互补 —— 售后卡单能扫是因为它有中间态，而取消退款、结算冲减、
 * 下单失败撤券/撤预占没有中间态，只能靠这张表。
 *
 * <p>并发控制两层：分布式锁保证同一轮只有一个实例在跑；单条任务还要 CAS 认领（0→3），
 * 因为"事务提交后的立即驱动"不在这把锁的保护范围内。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CompensationTaskScheduler {

    private static final String LOCK_RETRY_COMPENSATION = "byw:lock:task:compensation";

    private final CompensationTaskMapper taskMapper;
    private final CompensationService compensationService;
    private final DistributedLock distributedLock;

    /** 单轮最多处理多少条，避免积压时长时间持锁 */
    @Value("${byw.order.compensation-batch-size:100}")
    private int batchSize;

    /** "执行中"超过该分钟数仍未写回，认定上次执行没走完，放回待执行 */
    @Value("${byw.order.compensation-stale-minutes:10}")
    private int staleMinutes;

    @Scheduled(fixedDelayString = "${byw.order.compensation-scan-interval-ms:60000}")
    public void scanAndRun() {
        distributedLock.tryExecuteWithLock(LOCK_RETRY_COMPENSATION, 0, TimeUnit.SECONDS, () -> {
            try {
                int requeued = taskMapper.requeueStale(LocalDateTime.now().minusMinutes(staleMinutes));
                if (requeued > 0) {
                    // 出现即说明有执行方在"认领成功、写回结果之前"退出了，值得看一眼日志
                    log.warn("回收卡在「执行中」的补偿任务: {} 条（上次执行未写回结果）", requeued);
                }
                List<CompensationTask> due = taskMapper.selectDue(LocalDateTime.now(), batchSize);
                if (due.isEmpty()) {
                    return;
                }
                int processed = 0;
                for (CompensationTask task : due) {
                    if (taskMapper.claim(task.getId()) != 1) {
                        continue; // 被"立即驱动"抢走了
                    }
                    compensationService.executeClaimed(task);
                    processed++;
                }
                log.info("补偿任务本轮处理完成: 到期={}, 实际执行={}", due.size(), processed);
            } catch (Exception e) {
                log.error("补偿任务扫描执行失败", e);
            }
        });
    }
}
