package com.byw.order.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.byw.order.entity.Order;
import com.byw.order.entity.OrderItem;
import com.byw.order.entity.OrderStatusLog;
import com.byw.order.mapper.OrderItemMapper;
import com.byw.order.mapper.OrderMapper;
import com.byw.order.mapper.OrderStatusLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 秒杀订单落库事务单元。
 *
 * <p>独立 Bean 保证 {@code @Transactional} 经代理生效，把"订单 + 明细 + 状态日志"三次写入收敛为
 * 一个原子操作；配合 {@code t_order.uk_biz_key} 唯一索引，使 RocketMQ 的重复投递
 * （消费失败重试、多实例并发消费）只会生成一单。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SeckillOrderCreator {

    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final OrderStatusLogMapper orderStatusLogMapper;

    /**
     * 幂等落库秒杀订单。
     *
     * @param bizKey 业务幂等键（如 {@code seckill:12:34}），唯一索引是其最终保障
     * @return 本次是否真正创建；false 表示此前已创建过
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean create(String bizKey, Order order, OrderItem item, OrderStatusLog statusLog) {
        Long exists = orderMapper.selectCount(new LambdaQueryWrapper<Order>()
                .eq(Order::getBizKey, bizKey));
        if (exists != null && exists > 0) {
            log.info("秒杀订单已存在，跳过重复建单: bizKey={}", bizKey);
            return false;
        }
        order.setBizKey(bizKey);
        orderMapper.insert(order);
        item.setOrderId(order.getId());
        orderItemMapper.insert(item);
        statusLog.setOrderId(order.getId());
        orderStatusLogMapper.insert(statusLog);
        return true;
    }
}
