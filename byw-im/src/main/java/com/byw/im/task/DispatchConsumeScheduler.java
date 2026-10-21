package com.byw.im.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.byw.common.redis.lock.DistributedLock;
import com.byw.im.entity.Conversation;
import com.byw.im.mapper.ConversationMapper;
import com.byw.im.service.DispatchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * IM 分流兜底消费：每 30s 扫描存在排队/离线池会话的店铺，消费排队队列与离线消息池。
 * 触发点（客服上线/取消挂起/服务结束/掉线释放）之外的兜底保障，防漏分配。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DispatchConsumeScheduler {

    /** 分布式锁 key：多实例下同一轮只允许一个节点执行 */
    private static final String LOCK_DISPATCH_CONSUME = "byw:lock:task:im-dispatch-consume";

    private final ConversationMapper conversationMapper;
    private final DispatchService dispatchService;
    private final DistributedLock distributedLock;

    @Scheduled(fixedDelayString = "${byw.im.dispatch-consume-interval-ms:30000}")
    public void consumePending() {
        distributedLock.tryExecuteWithLock(LOCK_DISPATCH_CONSUME, 0, TimeUnit.SECONDS, this::consumePendingLocked);
    }

    /** 抢到锁后实际执行的消费逻辑 */
    private void consumePendingLocked() {
        try {
            // 仅处理存在排队/离线池会话的店铺，避免全量店铺空转
            List<Conversation> pending = conversationMapper.selectList(
                    new LambdaQueryWrapper<Conversation>().isNotNull(Conversation::getDispatchStatus));
            if (pending.isEmpty()) {
                return;
            }
            Set<Long> shopIds = pending.stream()
                    .map(Conversation::getShopId)
                    .filter(java.util.Objects::nonNull)
                    .collect(Collectors.toSet());
            for (Long shopId : shopIds) {
                try {
                    dispatchService.consumeQueue(shopId);
                    dispatchService.consumeOfflinePool(shopId);
                } catch (Exception e) {
                    log.warn("IM 定时消费队列失败：shopId={}, err={}", shopId, e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("IM 定时消费队列任务执行失败", e);
        }
    }
}
