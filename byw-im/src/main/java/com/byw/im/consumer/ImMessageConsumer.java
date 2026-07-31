package com.byw.im.consumer;

import com.byw.im.dto.ImBroadcast;
import com.byw.im.ws.SessionManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.MessageModel;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * IM 广播消费者（BROADCASTING 模式：每个 byw-im 节点各自消费全量消息）。
 * 收到事件后，向本节点持有的买家端(u:userId)与商家端(s:shopId)会话下推 {action, data} 帧。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = com.byw.common.rocketmq.constant.RocketMQTopics.IM_MESSAGE,
        consumerGroup = "byw-im-broadcast",
        messageModel = MessageModel.BROADCASTING
)
public class ImMessageConsumer implements RocketMQListener<String> {

    private final SessionManager sessionManager;
    private final ObjectMapper objectMapper;

    @Override
    public void onMessage(String message) {
        try {
            ImBroadcast event = objectMapper.readValue(message, ImBroadcast.class);
            Map<String, Object> envelope = new LinkedHashMap<>();
            envelope.put("action", event.getAction());
            envelope.put("data", event.getData());
            String payload = objectMapper.writeValueAsString(envelope);

            int sent = 0;
            if (event.getUserId() != null) {
                sent += sessionManager.sendToPrincipal(SessionManager.userPrincipal(event.getUserId()), payload);
            }
            if (event.getShopId() != null) {
                sent += sessionManager.sendToPrincipal(SessionManager.shopPrincipal(event.getShopId()), payload);
            }
            log.debug("IM 广播下推: action={}, userId={}, shopId={}, 本节点投递={}",
                    event.getAction(), event.getUserId(), event.getShopId(), sent);
        } catch (Exception e) {
            log.error("IM 广播消费失败: {}", message, e);
        }
    }
}
