package com.byw.order.producer;

import com.byw.common.kafka.constant.KafkaTopics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * 发送订单创建事件
     */
    public void sendOrderCreateEvent(String orderNo, Long userId) {
        Map<String, Object> event = Map.of(
                "orderNo", orderNo,
                "userId", userId,
                "eventType", "ORDER_CREATE",
                "timestamp", System.currentTimeMillis()
        );
        log.info("发送订单创建事件: orderNo={}", orderNo);
        kafkaTemplate.send(KafkaTopics.ORDER_CREATE, orderNo, event);
    }

    /**
     * 发送订单状态变更事件
     */
    public void sendOrderStatusChangeEvent(String orderNo, Integer fromStatus, Integer toStatus) {
        Map<String, Object> event = Map.of(
                "orderNo", orderNo,
                "fromStatus", fromStatus,
                "toStatus", toStatus,
                "eventType", "ORDER_STATUS_CHANGE",
                "timestamp", System.currentTimeMillis()
        );
        log.info("发送订单状态变更事件: orderNo={}, {} -> {}", orderNo, fromStatus, toStatus);
        kafkaTemplate.send(KafkaTopics.ORDER_STATUS_CHANGE, orderNo, event);
    }
}
