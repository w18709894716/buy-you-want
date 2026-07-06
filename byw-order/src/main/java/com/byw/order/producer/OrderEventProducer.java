package com.byw.order.producer;

import com.byw.common.rocketmq.constant.RocketMQTopics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventProducer {

    private final RocketMQTemplate rocketMQTemplate;

    /**
     * 延迟等级说明（RocketMQ 固定延迟级别）：
     * 1=1s  2=5s  3=10s  4=30s  5=1m  6=2m  7=3m  8=4m  9=5m
     * 10=6m 11=7m 12=8m  13=9m  14=10m 15=20m 16=30m 17=1h 18=2h
     * 订单超时使用等级 15（20分钟），覆盖15分钟超时场景
     */
    private static final int ORDER_TIMEOUT_DELAY_LEVEL = 15;

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
        rocketMQTemplate.syncSendOrderly(RocketMQTopics.ORDER_CREATE, event, orderNo);
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
        rocketMQTemplate.syncSendOrderly(RocketMQTopics.ORDER_STATUS_CHANGE, event, orderNo);
    }

    /**
     * 发送订单超时取消延迟消息
     * 使用 RocketMQ 延迟等级 15（约20分钟后投递），覆盖15分钟超时场景
     * 消费者收到消息后会校验订单状态，确保不会误取消已支付订单
     */
    public void sendOrderTimeoutCancelEvent(String orderNo) {
        Message<String> message = MessageBuilder.withPayload(orderNo).build();
        rocketMQTemplate.syncSendOrderly(
                RocketMQTopics.ORDER_TIMEOUT_CANCEL,
                message,
                orderNo,
                3000,
                ORDER_TIMEOUT_DELAY_LEVEL
        );
        log.info("发送订单超时取消延迟消息: orderNo={}, delayLevel={}（约20分钟后触发）", orderNo, ORDER_TIMEOUT_DELAY_LEVEL);
    }
}
