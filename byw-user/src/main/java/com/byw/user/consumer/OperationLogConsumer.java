package com.byw.user.consumer;

import com.byw.common.log.dto.OperationLogMessage;
import com.byw.common.rocketmq.constant.RocketMQTopics;
import com.byw.user.service.OperationLogService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

/**
 * 操作审计日志消费者：把各服务的 {@code @OperationLog} 切面发出的消息落到 t_operation_log。
 *
 * <p>异常策略与项目其他消费者一致：消息体无法解析（重试无意义）抛 IllegalArgumentException 尽快进死信；
 * 其余异常向上抛触发重试。消费本身是**纯追加**且不参与业务事务，重复消费只会多一条审计记录，
 * 因此这里对"解析成功但字段不全"的消息选择跳过并告警，而不是无限重试。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = RocketMQTopics.OPERATION_LOG,
        consumerGroup = "byw-user-operation-log-group",
        maxReconsumeTimes = 3
)
public class OperationLogConsumer implements RocketMQListener<String> {

    private final OperationLogService operationLogService;
    private final ObjectMapper objectMapper;

    @Override
    public void onMessage(String message) {
        if (message == null || message.isBlank()) {
            return;
        }
        OperationLogMessage parsed;
        try {
            parsed = objectMapper.readValue(message, OperationLogMessage.class);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("操作日志消息解析失败: " + message, e);
        }
        if (parsed.getModule() == null || parsed.getOperation() == null) {
            log.warn("操作日志消息缺少 module/operation，跳过: {}", message);
            return;
        }
        operationLogService.save(parsed);
        log.info("操作日志已落库: module={}, operation={}, operator={}, success={}",
                parsed.getModule(), parsed.getOperation(), parsed.getOperatorId(), parsed.isSuccess());
    }
}
