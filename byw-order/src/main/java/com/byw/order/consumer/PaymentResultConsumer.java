package com.byw.order.consumer;

import com.byw.common.kafka.constant.KafkaTopics;
import com.byw.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentResultConsumer {

    private final OrderService orderService;

    @KafkaListener(topics = KafkaTopics.PAYMENT_RESULT, groupId = "byw-order-group")
    public void handlePaymentResult(Map<String, Object> message) {
        String orderNo = (String) message.get("orderNo");
        Integer payStatus = (Integer) message.get("status");

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
