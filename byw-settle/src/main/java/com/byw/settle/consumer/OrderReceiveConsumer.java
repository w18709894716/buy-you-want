package com.byw.settle.consumer;

import com.byw.common.rocketmq.constant.RocketMQTopics;
import com.byw.settle.service.SettleService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 订单状态变更事件消费者：订单收货完成(status=3)时触发结算单生成。
 *
 * <p>异常策略与 byw-order 侧消费者保持一致：**只对重试无意义的内容抛 IllegalArgumentException
 * 尽快进死信，其余异常一律向上抛给 RocketMQ 触发重试**（重试耗尽后进入
 * {@code %DLQ%byw-settle-group}）。
 * 原实现 catch 所有异常只打日志，会把"结算单永久丢失"当成消费成功，属于资金链路的静默漏单。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = RocketMQTopics.ORDER_STATUS_CHANGE,
        consumerGroup = "byw-settle-group",
        maxReconsumeTimes = 3
)
public class OrderReceiveConsumer implements RocketMQListener<String> {

    private final SettleService settleService;
    private final ObjectMapper objectMapper;

    @Override
    public void onMessage(String message) {
        Map<String, Object> event;
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> parsed = objectMapper.readValue(message, Map.class);
            event = parsed;
        } catch (JsonProcessingException e) {
            // 消息体无法解析，重试也不会成功，尽快进死信人工排查
            throw new IllegalArgumentException("订单状态变更消息解析失败: " + message, e);
        }

        String orderNo = (String) event.get("orderNo");
        Integer toStatus = (Integer) event.get("toStatus");
        log.info("收到订单状态变更消息: orderNo={}, toStatus={}", orderNo, toStatus);

        // 可预期的跳过场景：直接 return（返回即视为消费成功）
        if (orderNo == null || toStatus == null) {
            log.warn("订单状态变更消息缺少关键字段，跳过: {}", message);
            return;
        }
        if (toStatus != 3) {
            return; // 仅关注收货完成（3=已完成）
        }
        // 结算失败/摇摆一律抛出重试：settleOnReceive 自身按订单号幂等，重复消费安全
        settleService.settleOnReceive(orderNo);
    }
}
