package com.byw.pay.producer;

/**
 * 支付回调事务消息的本地事务上下文。
 *
 * <p>随半消息在发送线程内传给本地事务监听器，不参与消息序列化、不发给消费者；
 * 消费者侧契约仍然只有 orderNo / payNo / status。
 */
public record PayCallbackContext(String payNo, String tradeNo, String callbackContent) {
}
