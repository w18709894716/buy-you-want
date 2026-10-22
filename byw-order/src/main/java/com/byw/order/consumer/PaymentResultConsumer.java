package com.byw.order.consumer;

import com.byw.common.core.exception.BusinessException;
import com.byw.common.rocketmq.constant.RocketMQTopics;
import com.byw.order.service.OrderService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 支付结果消费者。
 *
 * <p>异常一律抛出交给 RocketMQ 重试（重试耗尽后进入死信 {@code %DLQ%byw-order-group}），
 * 不再吞掉：若在此吞掉，消息会被当作消费成功、支付事件永久丢失，需人工补偿。
 * 重复投递这类可预期场景由 {@code OrderServiceImpl.updateStatus} 的同状态幂等返回兜住。
 *
 * <p>唯一的例外分支是"支付成功但订单已被取消关闭"：这不是需要重试的故障，而是一笔必须退的钱，
 * 所以就地发起退款而不是抛给 MQ。退款本身失败仍会抛错重试。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = RocketMQTopics.PAYMENT_RESULT,
        consumerGroup = "byw-order-group",
        maxReconsumeTimes = 3
)
public class PaymentResultConsumer implements RocketMQListener<String> {

    private final OrderService orderService;
    private final ObjectMapper objectMapper;

    @Override
    public void onMessage(String message) {
        Map<String, Object> event;
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> parsed = objectMapper.readValue(message, Map.class);
            event = parsed;
        } catch (JsonProcessingException e) {
            // 消息体无法解析，重试也不会成功，直接抛出让其尽快进入死信队列人工排查
            throw new IllegalArgumentException("支付结果消息解析失败: " + message, e);
        }

        String orderNo = (String) event.get("orderNo");
        Integer payStatus = (Integer) event.get("status");

        log.info("收到支付结果消息: orderNo={}, payStatus={}", orderNo, payStatus);

        if (payStatus != null && payStatus == 1) {
            // 支付成功，更新订单状态为待发货
            try {
                orderService.updateStatus(orderNo, 1);
            } catch (BusinessException e) {
                // 竞态：订单已被取消关闭（超时取消/用户取消抢先落库），状态机拒绝「已关闭 → 待发货」。
                // 钱已收、货不会发，必须原路退回。退款失败时 refundCancelledOnPaid 会抛错让 MQ 重试
                // （refNo=CANCEL:{orderNo} 幂等，重试不会重复退款）；不属于该场景则按原逻辑抛出。
                if (orderService.refundCancelledOnPaid(orderNo)) {
                    log.warn("支付成功但订单已取消，已完成原路退款: orderNo={}", orderNo);
                    return;
                }
                throw e;
            }
            log.info("订单支付成功，状态已更新为待发货: orderNo={}", orderNo);
        } else if (payStatus != null && payStatus == 2) {
            // 支付失败
            log.warn("订单支付失败: orderNo={}", orderNo);
        }
    }
}
