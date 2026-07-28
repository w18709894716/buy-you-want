package com.byw.settle.consumer;

import com.byw.common.rocketmq.constant.RocketMQTopics;
import com.byw.settle.service.SettleService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 订单状态变更事件消费者：订单收货完成(status=3)时触发结算单生成。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = RocketMQTopics.ORDER_STATUS_CHANGE,
        consumerGroup = "byw-settle-group"
)
public class OrderReceiveConsumer implements RocketMQListener<String> {

    private final SettleService settleService;
    private final ObjectMapper objectMapper;

    @Override
    public void onMessage(String message) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> event = objectMapper.readValue(message, Map.class);
            String orderNo = (String) event.get("orderNo");
            Integer toStatus = (Integer) event.get("toStatus");

            log.info("收到订单状态变更消息: orderNo={}, toStatus={}", orderNo, toStatus);

            // 仅关注收货完成（3=已完成）
            if (orderNo != null && toStatus != null && toStatus == 3) {
                settleService.settleOnReceive(orderNo);
            }
        } catch (Exception e) {
            log.error("处理订单状态变更消息失败: {}", message, e);
        }
    }
}
