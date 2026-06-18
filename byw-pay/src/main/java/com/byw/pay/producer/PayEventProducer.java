package com.byw.pay.producer;

import com.byw.common.kafka.constant.KafkaTopics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class PayEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * 发送支付结果消息
     */
    public void sendPaymentResult(String orderNo, String payNo, Integer status) {
        Map<String, Object> event = Map.of(
                "orderNo", orderNo,
                "payNo", payNo,
                "status", status,
                "eventType", "PAYMENT_RESULT",
                "timestamp", System.currentTimeMillis()
        );
        log.info("发送支付结果消息: orderNo={}, payNo={}, status={}", orderNo, payNo, status);
        kafkaTemplate.send(KafkaTopics.PAYMENT_RESULT, orderNo, event);
    }
}
