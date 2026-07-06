package com.byw.logistics.producer;

import com.byw.common.rocketmq.constant.RocketMQTopics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class LogisticsEventProducer {

    private final RocketMQTemplate rocketMQTemplate;

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
        rocketMQTemplate.syncSendOrderly(RocketMQTopics.LOGISTICS_UPDATE, event, orderNo);
    }
}
