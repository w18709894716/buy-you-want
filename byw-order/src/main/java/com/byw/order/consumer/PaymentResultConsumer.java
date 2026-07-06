package com.byw.order.consumer;

import com.byw.common.rocketmq.constant.RocketMQTopics;
import com.byw.order.service.OrderService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = RocketMQTopics.PAYMENT_RESULT,
        consumerGroup = "byw-order-group"
)
public class PaymentResultConsumer implements RocketMQListener<String> {

    private final OrderService orderService;
    private final ObjectMapper objectMapper;

    @Override
    public void onMessage(String message) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> event = objectMapper.readValue(message, Map.class);
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
        } catch (Exception e) {
            log.error("处理支付结果消息失败: {}", message, e);
        }
    }
}
