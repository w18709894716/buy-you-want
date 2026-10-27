package com.byw.order.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.byw.order.entity.CompensationTask;
import com.byw.order.mapper.CompensationTaskMapper;
import com.byw.order.service.CompensationService;
import com.byw.order.service.compensation.CompensationHandler;
import com.byw.order.service.compensation.CompensationType;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class CompensationServiceImpl implements CompensationService {

    /** last_error 列宽 VARCHAR(500)，超出截断，否则回写会二次失败 */
    private static final int MAX_ERROR_LENGTH = 500;
    /** 退避基数 30 秒，上限 30 分钟 */
    private static final long BACKOFF_BASE_SECONDS = 30L;
    private static final long BACKOFF_MAX_SECONDS = 1800L;

    private final CompensationTaskMapper taskMapper;
    private final ObjectMapper objectMapper;
    private final List<CompensationHandler> handlers;

    @Value("${byw.order.compensation-max-retry:7}")
    private int maxRetry;

    private Map<CompensationType, CompensationHandler> handlerMap;

    @PostConstruct
    void initHandlers() {
        Map<CompensationType, CompensationHandler> map = new EnumMap<>(CompensationType.class);
        for (CompensationHandler handler : handlers) {
            if (map.put(handler.type(), handler) != null) {
                throw new IllegalStateException("补偿处理器重复注册: " + handler.type());
            }
        }
        // 每个类型都必须有执行体：缺一个意味着登记出去的任务永远只能转"需人工"，
        // 而那时钱已经欠着了。启动期就失败比运行期静默好。
        for (CompensationType type : CompensationType.values()) {
            if (!map.containsKey(type)) {
                throw new IllegalStateException("补偿类型缺少处理器: " + type);
            }
        }
        this.handlerMap = Collections.unmodifiableMap(map);
        log.info("补偿任务处理器已就绪: {}", map.keySet());
    }

    @Override
    public void enqueueInCurrentTx(CompensationType type, String bizKey, String orderNo,
                                   String afterSaleNo, Object payload) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException(
                    "补偿任务必须在业务事务内登记，否则崩溃窗口依然存在: " + type + "/" + bizKey);
        }
        doEnqueue(type, bizKey, orderNo, afterSaleNo, payload);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void enqueueStandalone(CompensationType type, String bizKey, String orderNo,
                                  String afterSaleNo, Object payload) {
        doEnqueue(type, bizKey, orderNo, afterSaleNo, payload);
    }

    private void doEnqueue(CompensationType type, String bizKey, String orderNo,
                           String afterSaleNo, Object payload) {
        CompensationTask task = new CompensationTask();
        task.setTaskType(type.name());
        task.setBizKey(bizKey);
        task.setOrderNo(orderNo);
        task.setAfterSaleNo(afterSaleNo);
        task.setPayload(toJson(payload));
        task.setStatus(CompensationTask.STATUS_PENDING);
        task.setRetryCount(0);
        task.setNextRetryAt(LocalDateTime.now());
        try {
            taskMapper.insert(task);
        } catch (DuplicateKeyException e) {
            // 同一 (taskType, bizKey) 已登记：重复回调 / 重试 / 崩溃重放，不需要再来一条
            log.info("补偿任务已存在，跳过重复登记: type={}, bizKey={}", type, bizKey);
        }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void executeNow(CompensationType type, String bizKey) {
        // 独立事务：这个方法通常从 afterCommit 里被调用，此时原事务已提交但连接仍绑在
        // 当前线程上。直接在 afterCommit 里跑 SQL 会落到那条"已提交但未清理"的连接上，
        // 提交时机依赖驱动/TM 的细节（改配置就可能静默丢写）。REQUIRES_NEW 拿新连接、自己提交。
        CompensationTask task = taskMapper.selectOne(new LambdaQueryWrapper<CompensationTask>()
                .eq(CompensationTask::getTaskType, type.name())
                .eq(CompensationTask::getBizKey, bizKey));
        if (task == null) {
            log.warn("补偿任务不存在，跳过立即驱动: type={}, bizKey={}", type, bizKey);
            return;
        }
        if (task.getStatus() == null || task.getStatus() != CompensationTask.STATUS_PENDING) {
            return; // 已完成 / 已转人工 / 正被调度器执行
        }
        if (taskMapper.claim(task.getId()) != 1) {
            return; // 与调度器抢同一条，让对方执行
        }
        executeClaimed(task);
    }

    @Override
    public void executeClaimed(CompensationTask task) {
        CompensationType type;
        try {
            type = CompensationType.valueOf(task.getTaskType());
        } catch (IllegalArgumentException | NullPointerException e) {
            writeResult(task.getId(), CompensationTask.STATUS_MANUAL, retryCountOf(task),
                    LocalDateTime.now(), "未知的补偿类型: " + task.getTaskType());
            return;
        }
        CompensationHandler handler = handlerMap.get(type);
        if (handler == null) {
            writeResult(task.getId(), CompensationTask.STATUS_MANUAL, retryCountOf(task),
                    LocalDateTime.now(), "缺少处理器: " + type);
            return;
        }
        try {
            handler.execute(task);
            writeResult(task.getId(), CompensationTask.STATUS_DONE, retryCountOf(task),
                    LocalDateTime.now(), null);
        } catch (Exception e) {
            int retried = retryCountOf(task) + 1;
            String error = describe(e);
            if (retried > maxRetry) {
                log.error("补偿任务超过最大重试次数，转需人工: type={}, bizKey={}, retried={}, err={}",
                        type, task.getBizKey(), retried, error);
                writeResult(task.getId(), CompensationTask.STATUS_MANUAL, retried, LocalDateTime.now(), error);
            } else {
                LocalDateTime nextRetryAt = LocalDateTime.now().plusSeconds(backoffSeconds(retried));
                log.warn("补偿任务执行失败，稍后重试: type={}, bizKey={}, retried={}, next={}, err={}",
                        type, task.getBizKey(), retried, nextRetryAt, error);
                writeResult(task.getId(), CompensationTask.STATUS_PENDING, retried, nextRetryAt, error);
            }
        }
    }

    /** 30s、60s、120s…… 上限 30 分钟 */
    private long backoffSeconds(int retried) {
        int shift = Math.min(retried - 1, 16);
        return Math.min(BACKOFF_BASE_SECONDS << shift, BACKOFF_MAX_SECONDS);
    }

    private int retryCountOf(CompensationTask task) {
        return task.getRetryCount() == null ? 0 : task.getRetryCount();
    }

    private void writeResult(Long id, int status, int retryCount, LocalDateTime nextRetryAt, String error) {
        taskMapper.updateAfterRun(id, status, retryCount, nextRetryAt, truncate(error));
    }

    private String toJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            // 参数序列化失败属于编码错误，登记时就应该炸掉，而不是留一条执行不了的任务
            throw new IllegalStateException("补偿任务参数无法序列化: " + payload, e);
        }
    }

    private String describe(Exception e) {
        String message = e.getMessage();
        return message == null || message.isBlank()
                ? e.getClass().getSimpleName()
                : e.getClass().getSimpleName() + ": " + message;
    }

    private String truncate(String error) {
        if (error == null) {
            return null;
        }
        return error.length() <= MAX_ERROR_LENGTH ? error : error.substring(0, MAX_ERROR_LENGTH);
    }
}
