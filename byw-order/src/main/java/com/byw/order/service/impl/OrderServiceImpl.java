package com.byw.order.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.byw.api.cart.CartFeignClient;
import com.byw.api.order.dto.OrderCreateDTO;
import com.byw.api.order.dto.OrderDetailDTO;
import com.byw.api.product.ProductFeignClient;
import com.byw.api.product.dto.SkuStockDeductDTO;
import com.byw.api.promotion.PromotionFeignClient;
import com.byw.api.user.UserFeignClient;
import com.byw.api.user.dto.AddressDTO;
import com.byw.common.core.exception.BusinessException;
import com.byw.common.core.result.PageResult;
import com.byw.common.core.result.R;
import com.byw.order.entity.Order;
import com.byw.order.entity.OrderItem;
import com.byw.order.entity.OrderStatusLog;
import com.byw.order.mapper.OrderItemMapper;
import com.byw.order.mapper.OrderMapper;
import com.byw.order.mapper.OrderStatusLogMapper;
import com.byw.order.producer.OrderEventProducer;
import com.byw.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final OrderStatusLogMapper orderStatusLogMapper;
    private final ProductFeignClient productFeignClient;
    private final CartFeignClient cartFeignClient;
    private final PromotionFeignClient promotionFeignClient;
    private final UserFeignClient userFeignClient;
    private final OrderEventProducer orderEventProducer;

    /** 雪花ID计数器，用于生成唯一订单号 */
    private static final AtomicLong SEQUENCE = new AtomicLong(0);

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String createOrder(OrderCreateDTO createDTO) {
        // 1. 生成订单号: 日期 + 雪花序列
        String orderNo = generateOrderNo();
        Long userId = createDTO.getUserId();

        // 2. 计算订单金额并构建订单项
        BigDecimal totalAmount = BigDecimal.ZERO;
        List<OrderItem> orderItems = new java.util.ArrayList<>();

        for (OrderCreateDTO.OrderItemDTO itemDTO : createDTO.getItems()) {
            BigDecimal subtotal = itemDTO.getPrice().multiply(BigDecimal.valueOf(itemDTO.getQuantity()));
            totalAmount = totalAmount.add(subtotal);

            OrderItem orderItem = new OrderItem();
            orderItem.setOrderNo(orderNo);
            orderItem.setUserId(userId);
            orderItem.setProductId(itemDTO.getProductId());
            orderItem.setSkuId(itemDTO.getSkuId());
            orderItem.setProductName(itemDTO.getProductName());
            orderItem.setSkuName(itemDTO.getSkuName());
            orderItem.setProductImage(itemDTO.getProductImage());
            orderItem.setPrice(itemDTO.getPrice());
            orderItem.setQuantity(itemDTO.getQuantity());
            orderItem.setSubtotal(subtotal);
            orderItems.add(orderItem);
        }

        // 3. 使用优惠券
        BigDecimal discountAmount = BigDecimal.ZERO;
        if (createDTO.getCouponId() != null) {
            R<BigDecimal> couponResult = promotionFeignClient.useCoupon(
                    createDTO.getCouponId(), userId, totalAmount);
            if (couponResult.isSuccess() && couponResult.getData() != null) {
                discountAmount = couponResult.getData();
            }
        }

        // 4. 计算实付金额
        BigDecimal payAmount = totalAmount.subtract(discountAmount).max(BigDecimal.ZERO);

        // 5. 创建订单
        Order order = new Order();
        order.setOrderNo(orderNo);
        order.setUserId(userId);
        order.setTotalAmount(totalAmount);
        order.setPayAmount(payAmount);
        order.setFreightAmount(BigDecimal.ZERO);
        order.setDiscountAmount(discountAmount);
        order.setCouponId(createDTO.getCouponId());
        order.setStatus(0); // 待付款
        order.setReviewed(0); // 未评价
        order.setRemark(createDTO.getRemark());

        // 解析收货地址
        if (createDTO.getAddressId() != null) {
            try {
                R<AddressDTO> addrResult = userFeignClient.getAddressById(createDTO.getAddressId());
                if (addrResult.isSuccess() && addrResult.getData() != null) {
                    AddressDTO addr = addrResult.getData();
                    order.setReceiverName(addr.getReceiverName());
                    order.setReceiverPhone(addr.getReceiverPhone());
                    order.setReceiverAddress(
                            (addr.getProvince() != null ? addr.getProvince() : "") +
                            (addr.getCity() != null ? addr.getCity() : "") +
                            (addr.getDistrict() != null ? addr.getDistrict() : "") +
                            (addr.getDetailAddress() != null ? addr.getDetailAddress() : ""));
                }
            } catch (Exception e) {
                log.warn("获取收货地址失败，addressId={}: {}", createDTO.getAddressId(), e.getMessage());
            }
        }

        orderMapper.insert(order);

        // 6. 设置orderId并批量插入订单项
        for (OrderItem item : orderItems) {
            item.setOrderId(order.getId());
            orderItemMapper.insert(item);
        }

        // 7. 扣减库存
        List<SkuStockDeductDTO> deductList = createDTO.getItems().stream()
                .map(item -> new SkuStockDeductDTO(item.getSkuId(), item.getQuantity()))
                .collect(Collectors.toList());
        R<Boolean> stockResult = productFeignClient.deductStock(deductList);
        if (!stockResult.isSuccess()) {
            // 使用商品服务返回的具体错误信息（如“库存不足”）
            String msg = stockResult.getMessage() != null ? stockResult.getMessage() : "库存扣减失败";
            throw new BusinessException(msg);
        }

        // 8. 清除购物车中已下单商品
        List<Long> skuIds = createDTO.getItems().stream()
                .map(OrderCreateDTO.OrderItemDTO::getSkuId)
                .collect(Collectors.toList());
        cartFeignClient.clearCartItems(userId, skuIds);

        // 9. 保存状态日志
        saveStatusLog(order.getId(), null, 0, "系统", "创建订单");

        // 10. 发送订单创建RocketMQ消息
        orderEventProducer.sendOrderCreateEvent(orderNo, userId);

        // 11. 发送15分钟延迟消息，超时未支付自动取消
        orderEventProducer.sendOrderTimeoutCancelEvent(orderNo);

        log.info("订单创建成功: orderNo={}, userId={}", orderNo, userId);
        return orderNo;
    }

    @Override
    public OrderDetailDTO getOrderDetail(String orderNo) {
        Order order = getOrderByNo(orderNo);
        List<OrderItem> items = orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderNo, orderNo));

        return buildOrderDetailDTO(order, items);
    }

    @Override
    public PageResult<OrderDetailDTO> getUserOrders(Long userId, Integer status, Integer reviewed, Integer pageNum, Integer pageSize) {
        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<Order>()
                .eq(Order::getUserId, userId)
                .eq(status != null, Order::getStatus, status)
                // reviewed=0 精确匹配待评价；reviewed>=1 视为已评价（含已追评 2）
                .eq(reviewed != null && reviewed == 0, Order::getReviewed, 0)
                .ge(reviewed != null && reviewed >= 1, Order::getReviewed, 1)
                .orderByDesc(Order::getCreatedAt);

        Page<Order> page = new Page<>(pageNum, pageSize);
        Page<Order> orderPage = orderMapper.selectPage(page, wrapper);

        List<OrderDetailDTO> dtoList = orderPage.getRecords().stream().map(order -> {
            List<OrderItem> items = orderItemMapper.selectList(
                    new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderNo, order.getOrderNo()));
            return buildOrderDetailDTO(order, items);
        }).collect(Collectors.toList());

        return PageResult.of(dtoList, orderPage.getTotal(), pageNum, pageSize);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelOrder(String orderNo, String reason) {
        cancelOrder(orderNo, reason, "用户");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelOrder(String orderNo, String reason, String operator) {
        Order order = getOrderByNo(orderNo);

        // 只有待付款和待发货状态可以取消
        if (order.getStatus() != 0 && order.getStatus() != 1) {
            throw new BusinessException("当前订单状态不可取消");
        }

        Integer fromStatus = order.getStatus();
        order.setStatus(4); // 已取消
        order.setCancelTime(LocalDateTime.now());
        order.setCancelReason(reason);
        orderMapper.updateById(order);

        // 释放库存
        List<OrderItem> items = orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderNo, orderNo));
        List<SkuStockDeductDTO> releaseList = items.stream()
                .map(item -> new SkuStockDeductDTO(item.getSkuId(), item.getQuantity()))
                .collect(Collectors.toList());
        productFeignClient.releaseStock(releaseList);

        // 释放优惠券
        if (order.getCouponId() != null) {
            promotionFeignClient.releaseCoupon(order.getCouponId(), order.getUserId());
        }

        // 保存状态日志
        saveStatusLog(order.getId(), fromStatus, 4, operator, reason);

        // 发送状态变更事件
        orderEventProducer.sendOrderStatusChangeEvent(orderNo, fromStatus, 4);

        log.info("订单取消成功: orderNo={}, operator={}", orderNo, operator);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void confirmReceive(String orderNo) {
        Order order = getOrderByNo(orderNo);

        if (order.getStatus() != 2) {
            throw new BusinessException("订单当前状态不可确认收货");
        }

        Integer fromStatus = order.getStatus();
        order.setStatus(3); // 已完成
        order.setReceiveTime(LocalDateTime.now());
        orderMapper.updateById(order);

        saveStatusLog(order.getId(), fromStatus, 3, "用户", "确认收货");
        orderEventProducer.sendOrderStatusChangeEvent(orderNo, fromStatus, 3);

        log.info("订单确认收货: orderNo={}", orderNo);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(String orderNo, Integer status) {
        Order order = getOrderByNo(orderNo);
        Integer fromStatus = order.getStatus();

        order.setStatus(status);
        if (status == 1) {
            order.setPayTime(LocalDateTime.now());
        } else if (status == 2) {
            order.setShipTime(LocalDateTime.now());
        }
        orderMapper.updateById(order);

        saveStatusLog(order.getId(), fromStatus, status, "系统", "状态更新");
        orderEventProducer.sendOrderStatusChangeEvent(orderNo, fromStatus, status);
    }

    @Override
    public java.util.Map<Integer, Integer> getOrderCountsByStatus(Long userId) {
        List<Order> orders = orderMapper.selectList(
                new LambdaQueryWrapper<Order>()
                        .eq(Order::getUserId, userId)
                        .in(Order::getStatus, 0, 1, 2, 3, 4)
                        .select(Order::getStatus, Order::getReviewed));

        java.util.Map<Integer, Integer> counts = new java.util.HashMap<>();
        counts.put(0, 0); // 待付款
        counts.put(1, 0); // 待发货
        counts.put(2, 0); // 待收货
        counts.put(3, 0); // 待评价（已完成未评价）
        counts.put(4, 0); // 已取消

        for (Order order : orders) {
            int status = order.getStatus();
            if (status == 3) {
                // 已完成订单，只有未评价的才算"待评价"
                if (order.getReviewed() == null || order.getReviewed() == 0) {
                    counts.put(3, counts.get(3) + 1);
                }
            } else {
                counts.put(status, counts.get(status) + 1);
            }
        }
        return counts;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateReviewed(String orderNo, Integer reviewed) {
        Order order = getOrderByNo(orderNo);
        order.setReviewed(reviewed);
        orderMapper.updateById(order);
        log.info("订单评价状态更新: orderNo={}, reviewed={}", orderNo, reviewed);
    }

    // ==================== 私有方法 ====================

    private Order getOrderByNo(String orderNo) {
        Order order = orderMapper.selectOne(
                new LambdaQueryWrapper<Order>().eq(Order::getOrderNo, orderNo));
        if (order == null) {
            throw new BusinessException("订单不存在");
        }
        return order;
    }

    @Override
    public String generateOrderNo() {
        String datePart = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        long seq = SEQUENCE.incrementAndGet() % 100000;
        return datePart + String.format("%05d", seq);
    }

    private void saveStatusLog(Long orderId, Integer fromStatus, Integer toStatus, String operator, String remark) {
        OrderStatusLog statusLog = new OrderStatusLog();
        statusLog.setOrderId(orderId);
        statusLog.setFromStatus(fromStatus);
        statusLog.setToStatus(toStatus);
        statusLog.setOperator(operator);
        statusLog.setRemark(remark);
        orderStatusLogMapper.insert(statusLog);
    }

    private OrderDetailDTO buildOrderDetailDTO(Order order, List<OrderItem> items) {
        OrderDetailDTO dto = new OrderDetailDTO();
        BeanUtils.copyProperties(order, dto);

        List<OrderDetailDTO.OrderItemDTO> itemDTOs = items.stream().map(item -> {
            OrderDetailDTO.OrderItemDTO itemDTO = new OrderDetailDTO.OrderItemDTO();
            BeanUtils.copyProperties(item, itemDTO);
            return itemDTO;
        }).collect(Collectors.toList());
        dto.setItems(itemDTOs);

        return dto;
    }
}
