package com.byw.logistics.producer;

import com.byw.common.kafka.constant.KafkaTopics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class LogisticsEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * 发送物流更新事件
     */
    public void sendLogisticsUpdateEvent(String orderNo, String trackingNo, String eventType) {
        Map<String, Object> event = Map.of(
                "orderNo", orderNo,
                "trackingNo", trackingNo,
                "eventType", eventType,
                "timestamp", System.currentTimeMillis()
        );
        log.info("发送物流更新事件: orderNo={}, trackingNo={}, eventType={}", orderNo, trackingNo, eventType);
        kafkaTemplate.send(KafkaTopics.LOGISTICS_UPDATE, orderNo, event);
    }
}
