package com.byw.im.producer;

import com.byw.common.rocketmq.constant.RocketMQTopics;
import com.byw.im.dto.ImBroadcast;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.stereotype.Component;

/**
 * IM 广播事件生产者：将消息/信令以 JSON 发布到广播 Topic，供所有 byw-im 节点消费后本地下推。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ImEventProducer {

    private final RocketMQTemplate rocketMQTemplate;
    private final ObjectMapper objectMapper;

    public void broadcast(ImBroadcast event) {
        try {
            String json = objectMapper.writeValueAsString(event);
            rocketMQTemplate.convertAndSend(RocketMQTopics.IM_MESSAGE, json);
        } catch (Exception e) {
            log.error("IM 广播事件发送失败: action={}", event.getAction(), e);
        }
    }
}
