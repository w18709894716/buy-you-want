package com.byw.order.consumer;

import com.byw.common.rocketmq.constant.RocketMQTopics;
import com.byw.order.service.OrderService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 支付结果消费者。
 *
 * <p>异常一律抛出交给 RocketMQ 重试（重试耗尽后进入死信 {@code %DLQ%byw-order-group}），
 * 不再吞掉：支付事件与超时取消竞态时 {@code updateStatus} 会拒绝非法流转并抛异常，
 * 若在此吞掉，消息会被当作消费成功、支付事件永久丢失，需人工补偿。
 * 重复投递这类可预期场景由 {@code OrderServiceImpl.updateStatus} 的同状态幂等返回兜住。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = RocketMQTopics.PAYMENT_RESULT,
        consumerGroup = "byw-order-group",
        maxReconsumeTimes = 3
)
public class PaymentResultConsumer implements RocketMQListener<String> {

    private final OrderService orderService;
    private final ObjectMapper objectMapper;

    @Override
    public void onMessage(String message) {
        Map<String, Object> event;
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> parsed = objectMapper.readValue(message, Map.class);
            event = parsed;
        } catch (JsonProcessingException e) {
            // 消息体无法解析，重试也不会成功，直接抛出让其尽快进入死信队列人工排查
            throw new IllegalArgumentException("支付结果消息解析失败: " + message, e);
        }

        String orderNo = (String) event.get("orderNo");
        Integer payStatus = (Integer) event.get("status");

        log.info("收到支付结果消息: orderNo={}, payStatus={}", orderNo, payStatus);

        if (payStatus != null && payStatus == 1) {
            // 支付成功，更新订单状态为待发货
            orderService.updateStatus(orderNo, 1);
            log.info("订单支付成功，状态已更新为待发货: orderNo={}", orderNo);
        } else if (payStatus != null && payStatus == 2) {
            // 支付失败
            log.warn("订单支付失败: orderNo={}", orderNo);
        }
    }
}
