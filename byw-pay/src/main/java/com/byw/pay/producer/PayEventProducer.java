package com.byw.pay.producer;

import com.byw.common.rocketmq.constant.RocketMQTopics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.LocalTransactionState;
import org.apache.rocketmq.client.producer.TransactionSendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class PayEventProducer {

    private final RocketMQTemplate rocketMQTemplate;

    /**
     * 以事务消息发送支付成功事件。
     *
     * @param arg 本地事务上下文，由 {@link PayTransactionListener} 接收并执行"支付单置为已支付"
     * @return 本地事务是否已提交；false 表示本地事务回滚、半消息不会投递，调用方应让回调失败以便渠道重试
     */
    public boolean sendPaymentResultInTransaction(String orderNo, String payNo, Object arg) {
        Map<String, Object> event = Map.of(
                "orderNo", orderNo,
                "payNo", payNo,
                "status", 1,
                "eventType", "PAYMENT_RESULT",
                "timestamp", System.currentTimeMillis()
        );
        Message<Map<String, Object>> message = MessageBuilder.withPayload(event).build();
        log.info("发送支付结果事务消息: orderNo={}, payNo={}", orderNo, payNo);
        TransactionSendResult result = rocketMQTemplate.sendMessageInTransaction(
                RocketMQTopics.PAYMENT_RESULT, message, arg);
        return result != null && LocalTransactionState.COMMIT_MESSAGE.equals(result.getLocalTransactionState());
    }
}
