package com.byw.order.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.byw.api.cart.CartFeignClient;
import com.byw.api.logistics.LogisticsFeignClient;
import com.byw.api.logistics.dto.LogisticsDTO;
import com.byw.api.logistics.dto.ShipRequestDTO;
import com.byw.api.order.dto.OrderCreateDTO;
import com.byw.api.order.dto.OrderDetailDTO;
import com.byw.api.product.ProductFeignClient;
import com.byw.api.product.dto.SkuDTO;
import com.byw.api.product.dto.SkuStockDeductDTO;
import com.byw.api.promotion.PromotionFeignClient;
import com.byw.api.shop.ShopFeignClient;
import com.byw.api.shop.dto.ShopDTO;
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
    private final LogisticsFeignClient logisticsFeignClient;
    private final OrderEventProducer orderEventProducer;
    private final ShopFeignClient shopFeignClient;

    /** 雪花ID计数器，用于生成唯一订单号 */
    private static final AtomicLong SEQUENCE = new AtomicLong(0);

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String createOrder(OrderCreateDTO createDTO) {
        Long userId = createDTO.getUserId();
        List<OrderCreateDTO.OrderItemDTO> items = createDTO.getItems();
        if (items == null || items.isEmpty()) {
            throw new BusinessException("订单商品不能为空");
        }

        // 1. 回填每个下单项的店铺ID（前端可不传，从商品服务批量查询回填）
        backfillShopId(items);

        // 2. 按店铺分组（LinkedHashMap 保序，便于末店取余额分摊优惠）
        java.util.Map<Long, List<OrderCreateDTO.OrderItemDTO>> shopGroups = items.stream()
                .collect(Collectors.groupingBy(OrderCreateDTO.OrderItemDTO::getShopId,
                        java.util.LinkedHashMap::new, Collectors.toList()));

        // 3. 计算订单总额（跨店铺合计）
        BigDecimal grandTotal = items.stream()
                .map(i -> i.getPrice().multiply(BigDecimal.valueOf(i.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 4. 使用优惠券（整单一次核销，得到总优惠额）
        // 店铺券仅限券归属店铺的商品：以该店小计核销门槛/折扣，优惠只落到该店子订单
        BigDecimal totalDiscount = BigDecimal.ZERO;
        Long couponShopId = null; // 非空表示店铺券及其归属店铺；null 表示平台券或未用券
        if (createDTO.getCouponId() != null) {
            boolean couponUsable = true;
            try {
                R<com.byw.api.promotion.dto.CouponDTO> couponInfo =
                        promotionFeignClient.getCouponById(createDTO.getCouponId());
                if (couponInfo.isSuccess() && couponInfo.getData() != null) {
                    Long cShopId = couponInfo.getData().getShopId();
                    if (cShopId != null && cShopId != 0) {
                        couponShopId = cShopId;
                    }
                } else {
                    couponUsable = false;
                    log.warn("查询优惠券详情失败，本单跳过用券: couponId={}", createDTO.getCouponId());
                }
            } catch (Exception e) {
                couponUsable = false;
                log.warn("查询优惠券详情异常，本单跳过用券: couponId={}, error={}", createDTO.getCouponId(), e.getMessage());
            }
            BigDecimal couponBase = grandTotal;
            if (couponUsable && couponShopId != null) {
                List<OrderCreateDTO.OrderItemDTO> couponShopItems = shopGroups.get(couponShopId);
                if (couponShopItems == null) {
                    throw new BusinessException("所选优惠券仅限指定店铺商品使用，请重新选择");
                }
                couponBase = couponShopItems.stream()
                        .map(i -> i.getPrice().multiply(BigDecimal.valueOf(i.getQuantity())))
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
            }
            if (couponUsable) {
                R<BigDecimal> couponResult = promotionFeignClient.useCoupon(
                        createDTO.getCouponId(), userId, couponBase);
                if (couponResult.isSuccess() && couponResult.getData() != null) {
                    totalDiscount = couponResult.getData();
                }
            }
        }

        // 5. 解析收货地址（父订单与各子订单共用）
        String[] receiver = resolveReceiver(createDTO.getAddressId());

        // 6. 生成父订单号并创建父订单（聚合支付，无明细，不归属单一店铺）
        String parentOrderNo = generateOrderNo();
        BigDecimal parentPayAmount = grandTotal.subtract(totalDiscount).max(BigDecimal.ZERO);
        Order parent = new Order();
        parent.setOrderNo(parentOrderNo);
        parent.setParentOrderNo(null);
        parent.setIsParent(1);
        parent.setUserId(userId);
        parent.setShopId(null);
        parent.setTotalAmount(grandTotal);
        parent.setPayAmount(parentPayAmount);
        parent.setFreightAmount(BigDecimal.ZERO);
        parent.setDiscountAmount(totalDiscount);
        parent.setCouponId(createDTO.getCouponId());
        parent.setStatus(0); // 待付款
        parent.setReviewed(0);
        parent.setRemark(createDTO.getRemark());
        applyReceiver(parent, receiver);
        orderMapper.insert(parent);
        saveStatusLog(parent.getId(), null, 0, "系统", "创建父订单");

        // 7. 逐店铺创建子订单（优惠券按金额比例分摊，末店取余额消除舍入误差）
        BigDecimal allocatedDiscount = BigDecimal.ZERO;
        int shopIndex = 0;
        int shopCount = shopGroups.size();
        for (java.util.Map.Entry<Long, List<OrderCreateDTO.OrderItemDTO>> entry : shopGroups.entrySet()) {
            shopIndex++;
            Long shopId = entry.getKey();
            List<OrderCreateDTO.OrderItemDTO> shopItems = entry.getValue();

            BigDecimal shopTotal = shopItems.stream()
                    .map(i -> i.getPrice().multiply(BigDecimal.valueOf(i.getQuantity())))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            // 分摊优惠：店铺券全额落到券归属店铺；平台券非末店按比例、末店取剩余，确保子订单优惠合计=父订单总优惠
            BigDecimal shopDiscount;
            if (couponShopId != null) {
                shopDiscount = couponShopId.equals(shopId) ? totalDiscount : BigDecimal.ZERO;
            } else if (shopIndex == shopCount) {
                shopDiscount = totalDiscount.subtract(allocatedDiscount).max(BigDecimal.ZERO);
            } else if (grandTotal.compareTo(BigDecimal.ZERO) > 0) {
                shopDiscount = totalDiscount.multiply(shopTotal)
                        .divide(grandTotal, 2, java.math.RoundingMode.HALF_UP);
                allocatedDiscount = allocatedDiscount.add(shopDiscount);
            } else {
                shopDiscount = BigDecimal.ZERO;
            }
            BigDecimal shopPayAmount = shopTotal.subtract(shopDiscount).max(BigDecimal.ZERO);

            String childOrderNo = generateOrderNo();
            Order child = new Order();
            child.setOrderNo(childOrderNo);
            child.setParentOrderNo(parentOrderNo);
            child.setIsParent(0);
            child.setUserId(userId);
            child.setShopId(shopId);
            child.setTotalAmount(shopTotal);
            child.setPayAmount(shopPayAmount);
            child.setFreightAmount(BigDecimal.ZERO);
            child.setDiscountAmount(shopDiscount);
            child.setCouponId(null); // 优惠券归属父订单，子订单仅记录分摊金额
            child.setStatus(0); // 待付款（父订单支付成功后联动置1）
            child.setReviewed(0);
            child.setRemark(createDTO.getRemark());
            applyReceiver(child, receiver);
            orderMapper.insert(child);

            for (OrderCreateDTO.OrderItemDTO itemDTO : shopItems) {
                BigDecimal subtotal = itemDTO.getPrice().multiply(BigDecimal.valueOf(itemDTO.getQuantity()));
                OrderItem orderItem = new OrderItem();
                orderItem.setOrderId(child.getId());
                orderItem.setOrderNo(childOrderNo);
                orderItem.setUserId(userId);
                orderItem.setShopId(shopId);
                orderItem.setProductId(itemDTO.getProductId());
                orderItem.setSkuId(itemDTO.getSkuId());
                orderItem.setProductName(itemDTO.getProductName());
                orderItem.setSkuName(itemDTO.getSkuName());
                orderItem.setProductImage(itemDTO.getProductImage());
                orderItem.setPrice(itemDTO.getPrice());
                orderItem.setQuantity(itemDTO.getQuantity());
                orderItem.setSubtotal(subtotal);
                orderItem.setShipStatus(0);
                orderItemMapper.insert(orderItem);
            }
            saveStatusLog(child.getId(), null, 0, "系统", "创建子订单");
        }

        // 8. 扣减库存（全部明细一次性扣减）
        List<SkuStockDeductDTO> deductList = items.stream()
                .map(item -> new SkuStockDeductDTO(item.getSkuId(), item.getQuantity()))
                .collect(Collectors.toList());
        R<Boolean> stockResult = productFeignClient.deductStock(deductList);
        if (!stockResult.isSuccess()) {
            // 使用商品服务返回的具体错误信息（如“库存不足”）
            String msg = stockResult.getMessage() != null ? stockResult.getMessage() : "库存扣减失败";
            throw new BusinessException(msg);
        }

        // 9. 清除购物车中已下单商品
        List<Long> skuIds = items.stream()
                .map(OrderCreateDTO.OrderItemDTO::getSkuId)
                .collect(Collectors.toList());
        cartFeignClient.clearCartItems(userId, skuIds);

        // 10. 发送订单创建事件 + 超时未支付自动取消延迟消息（均以父订单为维度）
        orderEventProducer.sendOrderCreateEvent(parentOrderNo, userId);
        orderEventProducer.sendOrderTimeoutCancelEvent(parentOrderNo);

        log.info("订单创建成功: parentOrderNo={}, userId={}, 拆分店铺数={}", parentOrderNo, userId, shopCount);
        return parentOrderNo;
    }

    /** 回填下单项店铺ID：前端未传时从商品服务批量查询；仍缺失则兜底为自营店铺1 */
    private void backfillShopId(List<OrderCreateDTO.OrderItemDTO> items) {
        List<Long> missingSkuIds = items.stream()
                .filter(i -> i.getShopId() == null)
                .map(OrderCreateDTO.OrderItemDTO::getSkuId)
                .distinct()
                .collect(Collectors.toList());
        if (!missingSkuIds.isEmpty()) {
            try {
                R<List<SkuDTO>> skuResult = productFeignClient.getSkuByIds(missingSkuIds);
                if (skuResult.isSuccess() && skuResult.getData() != null) {
                    java.util.Map<Long, Long> skuShopMap = skuResult.getData().stream()
                            .filter(s -> s.getShopId() != null)
                            .collect(Collectors.toMap(SkuDTO::getId, SkuDTO::getShopId, (a, b) -> a));
                    for (OrderCreateDTO.OrderItemDTO item : items) {
                        if (item.getShopId() == null) {
                            item.setShopId(skuShopMap.get(item.getSkuId()));
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("回填订单项店铺ID失败: {}", e.getMessage());
            }
        }
        // 兜底：仍为空的默认归属自营店铺1，避免分组时 NPE
        for (OrderCreateDTO.OrderItemDTO item : items) {
            if (item.getShopId() == null) {
                item.setShopId(1L);
            }
        }
    }

    /** 解析收货地址，返回 [收货人, 电话, 完整地址]；获取失败时返回全 null 数组 */
    private String[] resolveReceiver(Long addressId) {
        String[] receiver = new String[3];
        if (addressId == null) {
            return receiver;
        }
        try {
            R<AddressDTO> addrResult = userFeignClient.getAddressById(addressId);
            if (addrResult.isSuccess() && addrResult.getData() != null) {
                AddressDTO addr = addrResult.getData();
                receiver[0] = addr.getReceiverName();
                receiver[1] = addr.getReceiverPhone();
                receiver[2] = (addr.getProvince() != null ? addr.getProvince() : "") +
                        (addr.getCity() != null ? addr.getCity() : "") +
                        (addr.getDistrict() != null ? addr.getDistrict() : "") +
                        (addr.getDetailAddress() != null ? addr.getDetailAddress() : "");
            }
        } catch (Exception e) {
            log.warn("获取收货地址失败，addressId={}: {}", addressId, e.getMessage());
        }
        return receiver;
    }

    /** 将解析后的收货信息写入订单 */
    private void applyReceiver(Order order, String[] receiver) {
        order.setReceiverName(receiver[0]);
        order.setReceiverPhone(receiver[1]);
        order.setReceiverAddress(receiver[2]);
    }

    @Override
    public OrderDetailDTO getOrderDetail(String orderNo) {
        Order order = getOrderByNo(orderNo);
        List<OrderItem> items = orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderNo, orderNo));

        OrderDetailDTO dto = buildOrderDetailDTO(order, items);
        fillShopNames(java.util.Collections.singletonList(dto));
        return dto;
    }

    @Override
    public PageResult<OrderDetailDTO> getUserOrders(Long userId, Integer status, Integer reviewed, Integer pageNum, Integer pageSize) {
        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<Order>()
                .eq(Order::getUserId, userId)
                // 仅展示子订单/独立订单，父订单仅用于聚合支付不出现在列表
                .eq(Order::getIsParent, 0)
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

        fillShopNames(dtoList);
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

        // 父订单：联动取消其全部未完成子订单，再取消父订单本身
        if (order.getIsParent() != null && order.getIsParent() == 1) {
            List<Order> children = orderMapper.selectList(
                    new LambdaQueryWrapper<Order>().eq(Order::getParentOrderNo, orderNo));
            for (Order child : children) {
                if (child.getStatus() != null && (child.getStatus() == 0 || child.getStatus() == 1)) {
                    doCancelChild(child, reason, operator);
                }
            }
            cancelParentRecord(order, reason, operator);
            return;
        }

        // 子订单 / 独立订单：仅待付款或待发货状态可取消
        if (order.getStatus() != 0 && order.getStatus() != 1) {
            throw new BusinessException("当前订单状态不可取消");
        }
        doCancelChild(order, reason, operator);

        // 若属于拆单：当同父下所有子订单均已取消时，联动取消父订单并释放优惠券
        if (order.getParentOrderNo() != null) {
            Order parent = orderMapper.selectOne(
                    new LambdaQueryWrapper<Order>().eq(Order::getOrderNo, order.getParentOrderNo()));
            if (parent != null && parent.getStatus() != null
                    && (parent.getStatus() == 0 || parent.getStatus() == 1)) {
                List<Order> siblings = orderMapper.selectList(
                        new LambdaQueryWrapper<Order>().eq(Order::getParentOrderNo, parent.getOrderNo()));
                boolean allCanceled = siblings.stream()
                        .allMatch(s -> s.getStatus() != null && s.getStatus() == 4);
                if (allCanceled) {
                    cancelParentRecord(parent, reason, operator);
                }
            }
        }
    }

    /** 取消单个子订单/独立订单：释放本单明细库存与自持优惠券 */
    private void doCancelChild(Order order, String reason, String operator) {
        Integer fromStatus = order.getStatus();
        order.setStatus(4); // 已取消
        order.setCancelTime(LocalDateTime.now());
        order.setCancelReason(reason);
        orderMapper.updateById(order);

        // 释放本单明细库存
        List<OrderItem> items = orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderNo, order.getOrderNo()));
        if (!items.isEmpty()) {
            List<SkuStockDeductDTO> releaseList = items.stream()
                    .map(item -> new SkuStockDeductDTO(item.getSkuId(), item.getQuantity()))
                    .collect(Collectors.toList());
            productFeignClient.releaseStock(releaseList);
        }

        // 释放优惠券：仅独立订单自身持券时释放；拆单子订单优惠券归属父订单，不在此处理
        if (order.getCouponId() != null) {
            promotionFeignClient.releaseCoupon(order.getCouponId(), order.getUserId());
        }

        saveStatusLog(order.getId(), fromStatus, 4, operator, reason);
        orderEventProducer.sendOrderStatusChangeEvent(order.getOrderNo(), fromStatus, 4);
        log.info("订单取消成功: orderNo={}, operator={}", order.getOrderNo(), operator);
    }

    /** 取消父订单记录本身：父订单无明细，仅释放整单优惠券 */
    private void cancelParentRecord(Order parent, String reason, String operator) {
        Integer fromStatus = parent.getStatus();
        parent.setStatus(4);
        parent.setCancelTime(LocalDateTime.now());
        parent.setCancelReason(reason);
        orderMapper.updateById(parent);

        if (parent.getCouponId() != null) {
            promotionFeignClient.releaseCoupon(parent.getCouponId(), parent.getUserId());
        }
        saveStatusLog(parent.getId(), fromStatus, 4, operator, reason);
        log.info("父订单取消成功: parentOrderNo={}, operator={}", parent.getOrderNo(), operator);
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

        // 父订单支付成功：联动其所有子订单置为待发货(1)
        if (status != null && status == 1 && order.getIsParent() != null && order.getIsParent() == 1) {
            List<Order> children = orderMapper.selectList(
                    new LambdaQueryWrapper<Order>().eq(Order::getParentOrderNo, orderNo));
            LocalDateTime payTime = order.getPayTime();
            for (Order child : children) {
                if (child.getStatus() != null && child.getStatus() != 0) {
                    continue; // 已流转的子订单不重复处理
                }
                Integer childFrom = child.getStatus();
                child.setStatus(1);
                child.setPayTime(payTime);
                orderMapper.updateById(child);
                saveStatusLog(child.getId(), childFrom, 1, "系统", "父订单支付成功");
                orderEventProducer.sendOrderStatusChangeEvent(child.getOrderNo(), childFrom, 1);
            }
            log.info("父订单支付成功联动子订单: parentOrderNo={}, 子订单数={}", orderNo, children.size());

            // 销量累加：仅首次 0->1 生效，明细在子订单上
            if (fromStatus == null || fromStatus == 0) {
                increaseProductSales(children.stream().map(Order::getOrderNo).collect(Collectors.toList()));
            }
        } else if (status != null && status == 1 && (fromStatus == null || fromStatus == 0)) {
            // 非父订单（如秒杀单）支付成功：直接按自身明细累加销量
            increaseProductSales(List.of(orderNo));
        }
    }

    /**
     * 按订单明细累加商品销量；失败仅记日志不阻断支付主流程
     */
    private void increaseProductSales(List<String> orderNos) {
        if (orderNos == null || orderNos.isEmpty()) {
            return;
        }
        try {
            List<OrderItem> items = orderItemMapper.selectList(
                    new LambdaQueryWrapper<OrderItem>().in(OrderItem::getOrderNo, orderNos));
            if (items.isEmpty()) {
                return;
            }
            List<SkuStockDeductDTO> salesList = items.stream()
                    .map(i -> new SkuStockDeductDTO(i.getSkuId(), i.getQuantity()))
                    .collect(Collectors.toList());
            productFeignClient.increaseSales(salesList);
        } catch (Exception e) {
            log.warn("累加商品销量失败: orderNos={}, err={}", orderNos, e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void shipItems(String orderNo, List<Long> itemIds, String companyName, String trackingNo) {
        // 1. 校验订单状态：仅待发货(1)或部分发货(7)可发货
        Order order = getOrderByNo(orderNo);
        Integer fromStatus = order.getStatus();
        if (fromStatus == null || (fromStatus != 1 && fromStatus != 7)) {
            throw new BusinessException("当前订单状态不可发货");
        }
        if (itemIds == null || itemIds.isEmpty()) {
            throw new BusinessException("请选择要发货的商品");
        }

        // 2. 校验选中明细均属于该订单且未发货
        List<OrderItem> allItems = orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderNo, orderNo));
        List<OrderItem> toShip = allItems.stream()
                .filter(i -> itemIds.contains(i.getId()))
                .collect(Collectors.toList());
        if (toShip.size() != itemIds.size()) {
            throw new BusinessException("存在不属于该订单的商品");
        }
        boolean anyShipped = toShip.stream().anyMatch(i -> i.getShipStatus() != null && i.getShipStatus() == 1);
        if (anyShipped) {
            throw new BusinessException("选中商品中存在已发货项");
        }

        // 3. 创建物流包裹，取回运单号
        ShipRequestDTO shipRequest = new ShipRequestDTO();
        shipRequest.setOrderNo(orderNo);
        shipRequest.setCompanyName(companyName);
        shipRequest.setTrackingNo(trackingNo);
        shipRequest.setReceiverName(order.getReceiverName());
        shipRequest.setReceiverPhone(order.getReceiverPhone());
        shipRequest.setReceiverAddress(order.getReceiverAddress());
        String finalTrackingNo = trackingNo;
        try {
            R<LogisticsDTO> shipResult = logisticsFeignClient.ship(shipRequest);
            if (shipResult != null && shipResult.isSuccess() && shipResult.getData() != null) {
                finalTrackingNo = shipResult.getData().getTrackingNo();
            }
        } catch (Exception e) {
            log.warn("创建物流包裹失败: orderNo={}, error={}", orderNo, e.getMessage());
        }

        // 4. 更新选中明细发货信息
        LocalDateTime now = LocalDateTime.now();
        for (OrderItem item : toShip) {
            item.setShipStatus(1);
            item.setTrackingNo(finalTrackingNo);
            item.setCompanyName(companyName);
            item.setShipTime(now);
            orderItemMapper.updateById(item);
        }

        // 5. 根据剩余未发货项重算订单状态
        boolean allShipped = allItems.stream()
                .allMatch(i -> itemIds.contains(i.getId())
                        || (i.getShipStatus() != null && i.getShipStatus() == 1));
        int toStatus = allShipped ? 2 : 7;
        order.setStatus(toStatus);
        if (allShipped) {
            order.setShipTime(now);
        }
        orderMapper.updateById(order);

        // 6. 记录状态日志与事件
        saveStatusLog(order.getId(), fromStatus, toStatus, "admin",
                allShipped ? "全部发货" : "部分发货");
        orderEventProducer.sendOrderStatusChangeEvent(orderNo, fromStatus, toStatus);

        log.info("订单发货: orderNo={}, 发货商品数={}, 全部发完={}", orderNo, toShip.size(), allShipped);
    }

    @Override
    public java.util.Map<Integer, Integer> getOrderCountsByStatus(Long userId) {
        List<Order> orders = orderMapper.selectList(
                new LambdaQueryWrapper<Order>()
                        .eq(Order::getUserId, userId)
                        // 仅统计子订单/独立订单，排除仅聚合支付的父订单
                        .eq(Order::getIsParent, 0)
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

    /** 批量回填订单归属店铺名称（拆单后子订单展示用），失败时静默跳过不影响主流程 */
    private void fillShopNames(List<OrderDetailDTO> dtoList) {
        if (dtoList == null || dtoList.isEmpty()) {
            return;
        }
        List<Long> shopIds = dtoList.stream()
                .map(OrderDetailDTO::getShopId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        if (shopIds.isEmpty()) {
            return;
        }
        try {
            R<List<ShopDTO>> shopResult = shopFeignClient.getShopsByIds(shopIds);
            if (shopResult.isSuccess() && shopResult.getData() != null) {
                java.util.Map<Long, String> shopNameMap = shopResult.getData().stream()
                        .collect(Collectors.toMap(ShopDTO::getId, ShopDTO::getName, (a, b) -> a));
                for (OrderDetailDTO dto : dtoList) {
                    if (dto.getShopId() != null) {
                        dto.setShopName(shopNameMap.get(dto.getShopId()));
                    }
                }
            }
        } catch (Exception e) {
            log.warn("回填订单店铺名称失败: {}", e.getMessage());
        }
    }
}
