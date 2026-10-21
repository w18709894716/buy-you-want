package com.byw.order.consumer;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.byw.common.rocketmq.constant.RocketMQTopics;
import com.byw.order.entity.Order;
import com.byw.order.mapper.OrderMapper;
import com.byw.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

/**
 * 订单超时取消消费者。
 *
 * <p>可预期的场景（订单不存在、已非待付款）直接跳过；其余异常不再吞掉，
 * 抛出交由 RocketMQ 重试，重试耗尽后进入死信 {@code %DLQ%byw-order-timeout-group}。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = RocketMQTopics.ORDER_TIMEOUT_CANCEL,
        consumerGroup = "byw-order-timeout-group",
        maxReconsumeTimes = 3
)
public class OrderTimeoutCancelConsumer implements RocketMQListener<String> {

    private final OrderMapper orderMapper;
    private final OrderService orderService;

    @Override
    public void onMessage(String orderNo) {
        log.info("收到订单超时取消消息: orderNo={}", orderNo);

        // 查询订单当前状态
        Order order = orderMapper.selectOne(
                new LambdaQueryWrapper<Order>().eq(Order::getOrderNo, orderNo));

        if (order == null) {
            log.warn("订单不存在，跳过超时取消: orderNo={}", orderNo);
            return;
        }

        // 只有待付款(status=0)的订单才需要自动取消
        if (order.getStatus() != 0) {
            log.info("订单已非待付款状态，跳过超时取消: orderNo={}, status={}", orderNo, order.getStatus());
            return;
        }

        // 自动取消订单
        orderService.cancelOrder(orderNo, "超时未支付，系统自动取消", "系统");
        log.info("订单超时自动取消成功: orderNo={}", orderNo);
    }
}
