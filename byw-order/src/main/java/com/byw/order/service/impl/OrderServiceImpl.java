package com.byw.order.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.byw.api.cart.CartFeignClient;
import com.byw.api.logistics.LogisticsFeignClient;
import com.byw.api.logistics.dto.LogisticsDTO;
import com.byw.api.logistics.dto.ShipRequestDTO;
import com.byw.api.order.dto.OrderCreateDTO;
import com.byw.api.order.dto.OrderDetailDTO;
import com.byw.api.pay.PayFeignClient;
import com.byw.api.product.ProductFeignClient;
import com.byw.api.product.dto.ProductDTO;
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
import com.byw.order.entity.AfterSale;
import com.byw.order.mapper.OrderItemMapper;
import com.byw.order.mapper.OrderMapper;
import com.byw.order.mapper.OrderStatusLogMapper;
import com.byw.order.mapper.AfterSaleMapper;
import com.byw.common.core.util.SnowflakeIdGenerator;
import com.byw.common.redis.util.RedisUtil;
import com.byw.order.producer.OrderEventProducer;
import com.byw.order.service.CompensationService;
import com.byw.order.service.OrderService;
import com.byw.order.service.compensation.CompensationPayloads;
import com.byw.order.service.compensation.CompensationType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.DigestUtils;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    /** 幂等键"首单处理中"占位值：抢到键但订单尚未落库时写入，重复请求据此判断是"处理中"而非"已完成" */
    private static final String IDEMPOTENT_PENDING = "PENDING";

    /** 单个 SKU 单次下单数量上限，同时也是数量下界校验的锚点（负数会反向增加库存） */
    private static final int MAX_ITEM_QUANTITY = 999;

    /** 商品未回填店铺时的兜底自营店铺ID（与 t_order.shop_id 建表默认值一致） */
    private static final long DEFAULT_SELF_SHOP_ID = 1L;

    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final OrderStatusLogMapper orderStatusLogMapper;
    private final AfterSaleMapper afterSaleMapper;
    private final ProductFeignClient productFeignClient;
    private final CartFeignClient cartFeignClient;
    private final PromotionFeignClient promotionFeignClient;
    private final PayFeignClient payFeignClient;
    private final UserFeignClient userFeignClient;
    private final LogisticsFeignClient logisticsFeignClient;
    private final OrderEventProducer orderEventProducer;
    private final ShopFeignClient shopFeignClient;
    private final RedisUtil redisUtil;
    private final CompensationService compensationService;

    /** 幂等键存活时长（秒）：覆盖用户连点/网络重试的时间窗，过期后允许同内容重新下单 */
    @Value("${byw.order.idempotent-ttl-seconds:30}")
    private long idempotentTtlSeconds;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String createOrder(OrderCreateDTO createDTO) {
        Long userId = createDTO.getUserId();
        List<OrderCreateDTO.OrderItemDTO> items = createDTO.getItems();
        if (items == null || items.isEmpty()) {
            throw new BusinessException("订单商品不能为空");
        }
        validateItemQuantities(items);

        // 0. 幂等闸门（R12）：连点/前端重试/网络重发会送来内容完全相同的请求，
        // 必须在下单前挡住，否则每份重复请求都会各自核销券、各自预占库存。
        // 用"请求指纹"而非前端令牌：Agent/小程序等非页面调用方拿不到令牌，服务端算指纹天然覆盖全部入口。
        String idempotentKey = buildIdempotentKey(userId, createDTO, items);
        if (!Boolean.TRUE.equals(redisUtil.setIfAbsent(idempotentKey, IDEMPOTENT_PENDING,
                idempotentTtlSeconds, TimeUnit.SECONDS))) {
            Object stored = redisUtil.get(idempotentKey);
            if (stored != null && !IDEMPOTENT_PENDING.equals(stored)) {
                // 首单已成功落库：把首单订单号原样返回，对前端与 Agent 都是"下单成功"，零改造
                log.warn("重复下单命中幂等键，返回首单: key={}, orderNo={}", idempotentKey, stored);
                return stored.toString();
            }
            // 首单仍在处理中（或已失败但键尚未清理）：拒绝本次，让用户稍后重试
            throw new BusinessException("订单正在处理中，请勿重复提交");
        }
        // 抢到幂等键 = 本次是首单。若首单失败必须把键删掉，否则用户改不动内容、只能干等 TTL 过期。
        registerRollbackCompensation(() -> releaseIdempotentKey(idempotentKey));

        // 先生成父订单号：后面第 4 步核销优惠券就要用它作为回滚补偿的幂等键。
        // 之前是在第 6 步才生成，导致券补偿只能退而用"请求指纹"做键 —— 而同内容重试会产生
        // 相同的指纹，第二次失败时补偿任务会被唯一键判成"已登记"而不再执行，券就白扣了。
        // generateOrderNo 是纯雪花取号，提前调用无副作用。
        String parentOrderNo = generateOrderNo();

        // 1. 以商品服务为准回填价格/归属店铺/商品快照：客户端传入的 price 只参与"是否已变更"的比对，
        //    绝不作为下单金额（否则可直接构造 price=0.01 下单）
        applyAuthoritativeSkuInfo(items);

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
                if (!couponResult.isSuccess()) {
                    // 券业务失败（已过期/已使用/已下线/未达门槛等）：拒绝下单并原样返回原因。
                    // 原先这里是"静默跳过"——订单照常提交，用户按原价扣款却没有任何提示。
                    // 这类失败由券服务明确判定"未核销"，因此无需补偿。
                    String reason = couponResult.getMessage();
                    throw new BusinessException(reason != null && !reason.isBlank()
                            ? reason : "优惠券不可用，请重新选择");
                }
                // 券已核销：登记回滚补偿，本单后续任何一步失败（含提交阶段失败）都要把券还给用户。
                // 只在"确认核销成功"时登记 —— 调用超时导致结果未知时不敢盲撤，
                // 否则会把用户另一笔订单已用掉的券误恢复成未使用。
                Long usedCouponId = createDTO.getCouponId();
                registerRollbackCompensation(
                        () -> compensateReleaseCoupon(usedCouponId, userId, parentOrderNo));
                if (couponResult.getData() == null) {
                    // 核销已成功却没返回优惠金额：不能按"0 优惠"继续（用户会多付钱），
                    // 抛错让事务回滚，上面登记的补偿会把券退回。
                    throw new BusinessException("优惠券核销异常，请稍后重试");
                }
                totalDiscount = couponResult.getData();
            }
        }

        // 5. 解析收货地址（父订单与各子订单共用），并校验地址归属下单人
        String[] receiver = resolveReceiver(createDTO.getAddressId(), userId);

        // 6. 创建父订单（聚合支付，无明细，不归属单一店铺）；订单号已在第 0 步生成
        // 幂等键从占位更新为首单订单号：此后的重复请求将直接拿到这个号
        redisUtil.set(idempotentKey, parentOrderNo, idempotentTtlSeconds, TimeUnit.SECONDS);
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

        // 8. 锁定库存（下单预占：stock 减、lock_stock 加；支付成功再核销，未付取消则释放）
        List<SkuStockDeductDTO> deductList = items.stream()
                .map(item -> new SkuStockDeductDTO(item.getSkuId(), item.getQuantity()))
                .collect(Collectors.toList());
        R<Boolean> stockResult = productFeignClient.lockStock(deductList);
        if (!stockResult.isSuccess()) {
            // 使用商品服务返回的具体错误信息（如“库存不足”）
            String msg = stockResult.getMessage() != null ? stockResult.getMessage() : "库存锁定失败";
            throw new BusinessException(msg);
        }
        // 预占已生效：登记回滚补偿。lockStock 抛错时走不到这里，也不必补偿 ——
        // 库存不足是高频路径，此时上面登记的优惠券补偿会把券还给用户。
        registerRollbackCompensation(() -> compensateReleaseStock(deductList, parentOrderNo));

        // 9. 清除购物车中已下单商品：属于可以后补的副作用，不是订单成立的必要条件。
        // 失败只告警不回滚 —— 若让它把整单事务拖垮，会连带撤掉刚核销的券与刚预占的库存，
        // 用户拿到的却只是一个"下单失败"。
        List<Long> skuIds = items.stream()
                .map(OrderCreateDTO.OrderItemDTO::getSkuId)
                .collect(Collectors.toList());
        try {
            cartFeignClient.clearCartItems(userId, skuIds);
        } catch (Exception e) {
            log.warn("清除购物车失败，不影响下单: userId={}, skuIds={}, error={}", userId, skuIds, e.getMessage());
        }

        // 10. 发送订单创建事件 + 超时未支付自动取消延迟消息（均以父订单为维度）。
        orderEventProducer.sendOrderCreateEvent(parentOrderNo, userId);
        // 超时取消延迟消息不用事务消息：本地回滚而消息已投递时，消费者按 orderNo 查不到订单会直接跳过，
        // 反方向是安全的；消息真丢了由 OrderScheduler 的超时对账任务兜底。
        orderEventProducer.sendOrderTimeoutCancelEvent(parentOrderNo);

        log.info("订单创建成功: parentOrderNo={}, userId={}, 拆分店铺数={}", parentOrderNo, userId, shopCount);
        return parentOrderNo;
    }

    /**
     * 单次下单每个 SKU 的数量边界：必须为正整数且不超过上限。
     *
     * <p>数量不校验时，{@code quantity = -N} 会让 stock 扣减表达式变成 {@code stock + N}
     * （库存反向增加），必须在下单入口挡住。
     */
    private void validateItemQuantities(List<OrderCreateDTO.OrderItemDTO> items) {
        for (OrderCreateDTO.OrderItemDTO item : items) {
            Integer quantity = item.getQuantity();
            if (quantity == null || quantity < 1) {
                throw new BusinessException("商品数量必须大于 0");
            }
            if (quantity > MAX_ITEM_QUANTITY) {
                throw new BusinessException("单个商品数量不能超过 " + MAX_ITEM_QUANTITY);
            }
        }
    }

    /**
     * 服务端权威回填：价格、归属店铺、SKU 名称与图片一律取自商品服务。
     *
     * <p>客户端提交的 price 只用于比对"商品价格是否已变更"，不参与任何金额计算；
     * 不一致时直接拒绝，让用户刷新后重新提交，避免静默按新价成交。
     * 归属店铺同样以 SKU 为准，防止伪造 shopId 影响拆单与结算归属。
     */
    private void applyAuthoritativeSkuInfo(List<OrderCreateDTO.OrderItemDTO> items) {
        if (items.stream().anyMatch(i -> i.getSkuId() == null)) {
            throw new BusinessException("订单商品信息不完整，请返回购物车重新提交");
        }
        List<Long> skuIds = items.stream()
                .map(OrderCreateDTO.OrderItemDTO::getSkuId)
                .distinct()
                .collect(Collectors.toList());
        if (skuIds.size() != items.size()) {
            throw new BusinessException("同一商品请合并数量后提交");
        }

        R<List<SkuDTO>> skuResult = productFeignClient.getSkuByIds(skuIds);
        if (skuResult == null || !skuResult.isSuccess() || skuResult.getData() == null) {
            throw new BusinessException("商品信息查询失败，请稍后重试");
        }
        Map<Long, SkuDTO> skuMap = skuResult.getData().stream()
                .filter(s -> s.getId() != null)
                .collect(Collectors.toMap(SkuDTO::getId, s -> s, (a, b) -> a));

        for (OrderCreateDTO.OrderItemDTO item : items) {
            SkuDTO sku = skuMap.get(item.getSkuId());
            if (sku == null) {
                throw new BusinessException("商品不存在或已下架，请返回购物车重新提交");
            }
            if (sku.getPrice() == null || sku.getPrice().signum() < 0) {
                throw new BusinessException("商品价格异常，请稍后重试");
            }
            if (item.getPrice() != null && item.getPrice().compareTo(sku.getPrice()) != 0) {
                log.warn("下单价格与商品服务不一致，拒绝提交: skuId={}, clientPrice={}, serverPrice={}",
                        item.getSkuId(), item.getPrice(), sku.getPrice());
                throw new BusinessException("商品价格已更新，请返回购物车刷新后重新提交");
            }
            item.setPrice(sku.getPrice());
            item.setProductId(sku.getProductId());
            item.setShopId(sku.getShopId() != null ? sku.getShopId() : DEFAULT_SELF_SHOP_ID);
            if (sku.getSkuName() != null && !sku.getSkuName().isBlank()) {
                item.setSkuName(sku.getSkuName());
            }
            if (sku.getImage() != null && !sku.getImage().isBlank()) {
                item.setProductImage(sku.getImage());
            }
        }
        fillProductSnapshots(items);
    }

    /** 商品名称与主图快照：按商品维度补齐，失败退化为商品ID占位（仅影响展示，不影响金额） */
    private void fillProductSnapshots(List<OrderCreateDTO.OrderItemDTO> items) {
        Map<Long, ProductDTO> productCache = new HashMap<>();
        for (OrderCreateDTO.OrderItemDTO item : items) {
            Long productId = item.getProductId();
            if (productId == null) {
                continue;
            }
            if (!productCache.containsKey(productId)) {
                ProductDTO product = null;
                try {
                    R<ProductDTO> result = productFeignClient.getProductById(productId);
                    if (result != null && result.isSuccess()) {
                        product = result.getData();
                    }
                } catch (Exception e) {
                    log.warn("回填商品快照失败: productId={}, err={}", productId, e.getMessage());
                }
                productCache.put(productId, product);
            }
            ProductDTO product = productCache.get(productId);
            if (product == null) {
                continue;
            }
            if (product.getName() != null && !product.getName().isBlank()) {
                item.setProductName(product.getName());
            }
            if ((item.getProductImage() == null || item.getProductImage().isBlank())
                    && product.getMainImage() != null) {
                item.setProductImage(product.getMainImage());
            }
        }
        for (OrderCreateDTO.OrderItemDTO item : items) {
            if (item.getProductName() == null || item.getProductName().isBlank()) {
                item.setProductName("商品 " + item.getProductId());
            }
        }
    }

    /** 解析收货地址，返回 [收货人, 电话, 完整地址]；获取失败时返回全 null 数组 */
    private String[] resolveReceiver(Long addressId, Long userId) {
        String[] receiver = new String[3];
        if (addressId == null) {
            return receiver;
        }
        AddressDTO addr = null;
        try {
            R<AddressDTO> addrResult = userFeignClient.getAddressById(addressId);
            if (addrResult.isSuccess()) {
                addr = addrResult.getData();
            }
        } catch (Exception e) {
            log.warn("获取收货地址失败，addressId={}: {}", addressId, e.getMessage());
        }
        if (addr == null) {
            return receiver;
        }
        // addressId 由前端透传，不校验归属就能把他人地址的收货人/手机号/详址写进自己的订单
        // （下单后在订单详情里直接可读）。秒杀链路 SeckillOrderConsumer 已有同样的校验，这里补齐普通下单。
        // 抛错而非静默回退默认地址：回退会让攻击者以为成功，也会让正常用户收货到错误地址却毫无提示。
        if (!userId.equals(addr.getUserId())) {
            log.warn("下单地址归属校验失败：userId={}, addressId={}, addressUserId={}",
                    userId, addressId, addr.getUserId());
            throw new BusinessException("收货地址不可用，请重新选择");
        }
        receiver[0] = addr.getReceiverName();
        receiver[1] = addr.getReceiverPhone();
        receiver[2] = (addr.getProvince() != null ? addr.getProvince() : "") +
                (addr.getCity() != null ? addr.getCity() : "") +
                (addr.getDistrict() != null ? addr.getDistrict() : "") +
                (addr.getDetailAddress() != null ? addr.getDetailAddress() : "");
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
        fillAfterSaleSummary(java.util.Collections.singletonList(dto));
        return dto;
    }

    @Override
    public PageResult<OrderDetailDTO> getUserOrders(Long userId, Integer status, Integer reviewed, Integer pageNum, Integer pageSize) {
        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<Order>()
                .eq(Order::getUserId, userId)
                // 仅展示子订单/独立订单，父订单仅用于聚合支付不出现在列表
                .eq(Order::getIsParent, 0);
        // 待收货(2) tab 合并展示部分发货(7)，让部分发货订单对用户可见
        if (status != null && status == 2) {
            wrapper.in(Order::getStatus, 2, 7);
        } else if (status != null) {
            wrapper.eq(Order::getStatus, status);
        }
        wrapper
                // reviewed=0 精确匹配待评价；reviewed>=1 视为已评价（含已追评 2）
                .eq(reviewed != null && reviewed == 0, Order::getReviewed, 0)
                .ge(reviewed != null && reviewed >= 1, Order::getReviewed, 1)
                .orderByDesc(Order::getCreatedAt);

        Page<Order> page = new Page<>(pageNum, pageSize);
        Page<Order> orderPage = orderMapper.selectPage(page, wrapper);

        // 明细一次查完整页再按 orderNo 归组：原来每单一条 SQL，一页 10 条就是 10 次额外往返，
        // 且这些往返都在同一个请求线程里串行，翻页越频繁越明显。
        List<String> pageOrderNos = orderPage.getRecords().stream()
                .map(Order::getOrderNo)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        Map<String, List<OrderItem>> itemsByOrderNo = pageOrderNos.isEmpty()
                ? java.util.Collections.emptyMap()
                : orderItemMapper.selectList(new LambdaQueryWrapper<OrderItem>()
                        .in(OrderItem::getOrderNo, pageOrderNos))
                  .stream()
                  .collect(Collectors.groupingBy(OrderItem::getOrderNo));

        List<OrderDetailDTO> dtoList = orderPage.getRecords().stream()
                .map(order -> buildOrderDetailDTO(order,
                        itemsByOrderNo.getOrDefault(order.getOrderNo(), java.util.Collections.emptyList())))
                .collect(Collectors.toList());

        fillShopNames(dtoList);
        fillAfterSaleSummary(dtoList);
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
            Integer parentFromStatus = order.getStatus();
            // 父订单自身也必须处于可取消状态。此前这一支没有任何闸门，交易完成(3)/退款中(5)的
            // 父订单照样能被取消，cancelParentRecord 还会把整单优惠券退回 —— 而子订单的货早已发出，
            // 结果是"券复活 + 货照拿"。
            if (parentFromStatus == null || (parentFromStatus != 0 && parentFromStatus != 1)) {
                throw new BusinessException("当前订单状态不可取消");
            }
            List<Order> children = orderMapper.selectList(
                    new LambdaQueryWrapper<Order>().eq(Order::getParentOrderNo, orderNo));
            // 已有子订单进入履约（发货/收货/退款）时不允许整单取消：父单关闭后这些子订单
            // 会失去聚合支付载体，退款与结算都无从挂靠，只能逐商品走售后。
            for (Order child : children) {
                Integer childStatus = child.getStatus();
                if (childStatus != null && childStatus != 0 && childStatus != 1 && childStatus != 4) {
                    throw new BusinessException("部分商品已进入履约流程，请对该商品申请售后");
                }
            }
            for (Order child : children) {
                if (child.getStatus() != null && (child.getStatus() == 0 || child.getStatus() == 1)) {
                    doCancelChild(child, reason, operator);
                }
            }
            // 父订单不单独退款：父单实付 = 各子单实付之和（优惠按比例分摊、末店取余额），
            // 上面逐子单的退款既不漏也不重，且天然排除了此前已单独取消并退过款的子单。
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
        // CAS：仅当状态仍是读取到的值时才置为已取消。
        // 防止"超时取消"与"支付回调"并发时双双落库（后者会让库存被退回而订单又显示已支付）。
        int rows = orderMapper.update(null, new LambdaUpdateWrapper<Order>()
                .eq(Order::getId, order.getId())
                .eq(Order::getStatus, fromStatus)
                .set(Order::getStatus, 4)
                .set(Order::getCancelTime, LocalDateTime.now())
                .set(Order::getCancelReason, reason)
                .set(Order::getCloseType, 1));
        if (rows == 0) {
            log.warn("订单状态已变更，取消放弃: orderNo={}, fromStatus={}", order.getOrderNo(), fromStatus);
            throw new BusinessException("订单状态已变更，请刷新后重试");
        }
        order.setStatus(4);

        // 释放本单明细库存：未支付取消 → 释放下单预占；已支付待发货取消 → 退回可售库存
        List<OrderItem> items = orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderNo, order.getOrderNo()));
        if (!items.isEmpty()) {
            List<SkuStockDeductDTO> stockList = items.stream()
                    .map(item -> new SkuStockDeductDTO(item.getSkuId(), item.getQuantity()))
                    .collect(Collectors.toList());
            if (isSeckillOrder(order)) {
                log.info("秒杀订单取消跳过DB库存操作（Redis侧管理）: orderNo={}", order.getOrderNo());
            } else if (fromStatus != null && fromStatus == 1) {
                // 已支付：下单预占已在支付时核销，退回可售库存
                productFeignClient.restoreStock(stockList);
            } else {
                // 未支付：释放下单预占（stock 加回、lock_stock 减回）
                productFeignClient.releaseStock(stockList);
            }
        }

        // 释放优惠券：仅独立订单自身持券时释放；拆单子订单优惠券归属父订单，不在此处理
        if (order.getCouponId() != null) {
            promotionFeignClient.releaseCoupon(order.getCouponId(), order.getUserId());
        }

        saveStatusLog(order.getId(), fromStatus, 4, operator, reason);
        orderEventProducer.sendOrderStatusChangeEvent(order.getOrderNo(), fromStatus, 4);
        // 已支付（待发货）订单被取消：货没发、钱必须退。此前只回补库存不退钱，
        // 用户付了款却既拿不到货也拿不到退款。
        // 退款仍放在事务提交之后执行（放进事务里会被"远端已成功 + 本地回滚"放大成重复退款），
        // 但**任务在事务内登记**：这样即使进程在提交后、调用前崩溃，任务也已经在表里，
        // 由 CompensationTaskScheduler 接着重试，不会像以前那样只剩一行 error 日志。
        if (fromStatus != null && fromStatus == 1) {
            BigDecimal payAmount = order.getPayAmount();
            String refundOrderNo = order.getOrderNo();
            if (payAmount != null && payAmount.signum() > 0) {
                String bizKey = "CANCEL:" + refundOrderNo;
                compensationService.enqueueInCurrentTx(CompensationType.CANCEL_REFUND, bizKey,
                        refundOrderNo, null, new CompensationPayloads.CancelRefund(payAmount));
                registerAfterCommit(() -> compensationService.executeNow(
                        CompensationType.CANCEL_REFUND, bizKey));
            }
        }
        log.info("订单取消成功: orderNo={}, operator={}", order.getOrderNo(), operator);
    }

    /** 取消父订单记录本身：父订单无明细，仅释放整单优惠券 */
    private void cancelParentRecord(Order parent, String reason, String operator) {
        Integer fromStatus = parent.getStatus();
        int rows = orderMapper.update(null, new LambdaUpdateWrapper<Order>()
                .eq(Order::getId, parent.getId())
                .eq(Order::getStatus, fromStatus)
                .set(Order::getStatus, 4)
                .set(Order::getCancelTime, LocalDateTime.now())
                .set(Order::getCancelReason, reason)
                .set(Order::getCloseType, 1));
        if (rows == 0) {
            log.warn("父订单状态已变更，取消放弃: parentOrderNo={}, fromStatus={}", parent.getOrderNo(), fromStatus);
            throw new BusinessException("订单状态已变更，请刷新后重试");
        }
        parent.setStatus(4);

        if (parent.getCouponId() != null) {
            promotionFeignClient.releaseCoupon(parent.getCouponId(), parent.getUserId());
        }
        saveStatusLog(parent.getId(), fromStatus, 4, operator, reason);
        log.info("父订单取消成功: parentOrderNo={}, operator={}", parent.getOrderNo(), operator);
    }

    @Override
    public boolean refundCancelledOnPaid(String orderNo) {
        Order order = orderMapper.selectOne(
                new LambdaQueryWrapper<Order>().eq(Order::getOrderNo, orderNo));
        if (order == null || order.getStatus() == null || order.getStatus() != 4) {
            return false;
        }
        // 只处理"取消关闭"(close_type=1)：退款关闭(2)的钱已由售后链路退回，再退一次就是重复退款
        if (order.getCloseType() == null || order.getCloseType() != 1) {
            return false;
        }
        log.warn("支付成功但订单已取消关闭，发起原路退款: orderNo={}, payAmount={}", orderNo, order.getPayAmount());
        // 退款失败必须抛出让 MQ 重试：refNo 幂等，重试不会重复退款；
        // 若在此吞掉，消息被当作消费成功，这笔钱就只能等人工发现。
        if (!refundForCancel(orderNo, order.getPayAmount())) {
            throw new BusinessException("已取消订单的退款失败，等待重试");
        }
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void confirmReceive(String orderNo) {
        Order order = getOrderByNo(orderNo);

        if (order.getStatus() != 2) {
            throw new BusinessException("订单当前状态不可确认收货");
        }

        Integer fromStatus = order.getStatus();
        LocalDateTime receiveTime = LocalDateTime.now();
        int rows = orderMapper.update(null, new LambdaUpdateWrapper<Order>()
                .eq(Order::getId, order.getId())
                .eq(Order::getStatus, 2)
                .set(Order::getStatus, 3)
                .set(Order::getReceiveTime, receiveTime));
        if (rows == 0) {
            throw new BusinessException("订单状态已变更，请刷新后重试");
        }
        order.setStatus(3);
        order.setReceiveTime(receiveTime);

        saveStatusLog(order.getId(), fromStatus, 3, "用户", "确认收货");
        orderEventProducer.sendOrderStatusChangeEvent(orderNo, fromStatus, 3);

        log.info("订单确认收货: orderNo={}", orderNo);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(String orderNo, Integer status) {
        Order order = getOrderByNo(orderNo);
        Integer fromStatus = order.getStatus();

        // 幂等：目标状态与当前状态一致，直接返回（支付回调重复投递等场景）
        if (fromStatus != null && fromStatus.equals(status)) {
            log.info("订单已处于目标状态，忽略重复更新: orderNo={}, status={}", orderNo, status);
            return;
        }
        // 状态机校验：拒绝非法流转，防止「超时取消」与「支付回调」竞态把已关闭订单拉回待发货造成超卖
        if (!isStatusTransitionAllowed(fromStatus, status)) {
            log.warn("非法订单状态流转被拒绝: orderNo={}, {} -> {}", orderNo, fromStatus, status);
            throw new BusinessException("订单当前状态不允许该操作");
        }

        order.setStatus(status);
        if (status == 1) {
            order.setPayTime(LocalDateTime.now());
        } else if (status == 2) {
            order.setShipTime(LocalDateTime.now());
        }
        // 退款关闭：由退款流程驱动（退款中5/待发货1/待收货2/部分发货7 → 交易关闭4），标记为退款关闭以区分用户主动取消
        // 说明：用户主动取消不经过 updateStatus（走 doCancelChild 直接落库），此处 →4 均为售后退款链路
        if (fromStatus != null && status != null && status == 4
                && (fromStatus == 5 || fromStatus == 1 || fromStatus == 2 || fromStatus == 7)) {
            order.setCloseType(2);
        }
        // CAS：WHERE 带上读取到的原状态，并发下只有一个流转能成功。
        // 这是「支付回调 0→1」与「超时取消 0→4」竞态的收口闸门 ——
        // 两者都成功会出现"订单已支付、库存却已被 releaseStock 退回"的超卖。
        int rows = orderMapper.update(null, new LambdaUpdateWrapper<Order>()
                .eq(Order::getId, order.getId())
                .eq(Order::getStatus, fromStatus)
                .set(Order::getStatus, status)
                .set(status == 1, Order::getPayTime, order.getPayTime())
                .set(status == 2, Order::getShipTime, order.getShipTime())
                .set(order.getCloseType() != null, Order::getCloseType, order.getCloseType()));
        if (rows == 0) {
            log.warn("订单状态已被并发流转，本次放弃: orderNo={}, {} -> {}", orderNo, fromStatus, status);
            throw new BusinessException("订单状态已变更，请刷新后重试");
        }

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
                // CAS：子订单若被并发取消(0→4)，不再把它拉回待发货
                int childRows = orderMapper.update(null, new LambdaUpdateWrapper<Order>()
                        .eq(Order::getId, child.getId())
                        .eq(Order::getStatus, 0)
                        .set(Order::getStatus, 1)
                        .set(Order::getPayTime, payTime));
                if (childRows == 0) {
                    log.warn("子订单状态已变更，跳过支付联动: childOrderNo={}, fromStatus={}",
                            child.getOrderNo(), childFrom);
                    continue;
                }
                child.setStatus(1);
                child.setPayTime(payTime);
                saveStatusLog(child.getId(), childFrom, 1, "系统", "父订单支付成功");
                orderEventProducer.sendOrderStatusChangeEvent(child.getOrderNo(), childFrom, 1);
            }
            log.info("父订单支付成功联动子订单: parentOrderNo={}, 子订单数={}", orderNo, children.size());

            // 销量累加：仅首次 0->1 生效，明细在子订单上
            if (fromStatus == null || fromStatus == 0) {
                List<String> childOrderNos = children.stream().map(Order::getOrderNo).collect(Collectors.toList());
                consumeLockedStock(childOrderNos, order);
                increaseProductSales(childOrderNos);
            }
        } else if (status != null && status == 1 && (fromStatus == null || fromStatus == 0)) {
            // 非父订单（如秒杀单）支付成功：核销预占库存并按自身明细累加销量
            consumeLockedStock(List.of(orderNo), order);
            increaseProductSales(List.of(orderNo));
        }
    }

    /**
     * 支付成功核销下单预占的库存（lock_stock -= qty）；失败仅记日志不阻断支付主流程。
     * 秒杀订单库存在 promotion 服务的 Redis 侧预扣，从不写 product 库，故跳过 DB 核销。
     */
    private void consumeLockedStock(List<String> orderNos, Order paidOrder) {
        if (orderNos == null || orderNos.isEmpty()) {
            return;
        }
        if (isSeckillOrder(paidOrder)) {
            log.info("秒杀订单跳过DB库存核销（Redis侧预扣）: orderNo={}", paidOrder.getOrderNo());
            return;
        }
        try {
            List<OrderItem> items = orderItemMapper.selectList(
                    new LambdaQueryWrapper<OrderItem>().in(OrderItem::getOrderNo, orderNos));
            if (items.isEmpty()) {
                return;
            }
            List<SkuStockDeductDTO> consumeList = items.stream()
                    .map(i -> new SkuStockDeductDTO(i.getSkuId(), i.getQuantity()))
                    .collect(Collectors.toList());
            R<Boolean> result = productFeignClient.consumeStock(consumeList);
            if (result == null || !result.isSuccess()) {
                log.warn("库存核销失败: orderNos={}, msg={}", orderNos,
                        result == null ? "null" : result.getMessage());
            }
        } catch (Exception e) {
            log.warn("库存核销异常: orderNos={}, err={}", orderNos, e.getMessage());
        }
    }

    /**
     * 秒杀订单判定：以 {@code biz_key} 前缀为准，不采信 remark。
     *
     * <p>remark 来自下单入参（{@code createDTO.getRemark()}），普通订单也能写成"秒杀活动订单"；
     * 一旦伪造，本单支付时的库存核销、取消时的库存释放会被整体跳过，预占的 lock_stock 永久回不来。
     * biz_key 只由 {@link SeckillOrderCreator} 在消费秒杀消息时写入，普通下单恒为 NULL，无法伪造。
     */
    private boolean isSeckillOrder(Order order) {
        return order != null && SeckillOrderCreator.isSeckillBizKey(order.getBizKey());
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
        // 已退款/售后进行中的商品不可发货（确认收货前支持售后后，未发货商品可能已退款）
        Set<Long> refundedItemIds = selectRefundAfterSaleItemIds(orderNo, List.of(3));
        Set<Long> processingItemIds = selectRefundAfterSaleItemIds(orderNo, List.of(0, 1, 5, 6));
        if (toShip.stream().anyMatch(i -> refundedItemIds.contains(i.getId()) || processingItemIds.contains(i.getId()))) {
            throw new BusinessException("选中商品中存在已退款或售后处理中的商品，不可发货");
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

        // 5. 根据剩余未发货项重算订单状态：已完成退款的商品无需发货不计入待发范围；
        // 售后进行中的商品结局未定（被拒后仍需发货），仍计入待发，避免订单提前进入待收货后无法补发
        boolean allShipped = allItems.stream()
                .allMatch(i -> itemIds.contains(i.getId())
                        || (i.getShipStatus() != null && i.getShipStatus() == 1)
                        || refundedItemIds.contains(i.getId()));
        int toStatus = allShipped ? 2 : 7;
        order.setStatus(toStatus);
        if (allShipped) {
            order.setShipTime(now);
        }
        // CAS：发货期间若订单被并发流转（如售后退款关单），不覆盖对方结果
        int shipRows = orderMapper.update(null, new LambdaUpdateWrapper<Order>()
                .eq(Order::getId, order.getId())
                .eq(Order::getStatus, fromStatus)
                .set(Order::getStatus, toStatus)
                .set(allShipped, Order::getShipTime, now));
        if (shipRows == 0) {
            throw new BusinessException("订单状态已变更，请刷新后重试");
        }

        // 6. 记录状态日志与事件
        saveStatusLog(order.getId(), fromStatus, toStatus, "admin",
                allShipped ? "全部发货" : "部分发货");
        orderEventProducer.sendOrderStatusChangeEvent(orderNo, fromStatus, toStatus);

        log.info("订单发货: orderNo={}, 发货商品数={}, 全部发完={}", orderNo, toShip.size(), allShipped);
    }

    /** 查询订单下处于指定状态的退款类售后（仅退款/退货退款）商品明细 ID 集合 */
    private Set<Long> selectRefundAfterSaleItemIds(String orderNo, List<Integer> statuses) {
        return afterSaleMapper.selectList(new LambdaQueryWrapper<AfterSale>()
                        .eq(AfterSale::getOrderNo, orderNo)
                        .in(AfterSale::getType, 1, 2)
                        .in(AfterSale::getStatus, statuses)
                        .isNotNull(AfterSale::getOrderItemId)).stream()
                .map(AfterSale::getOrderItemId)
                .collect(Collectors.toSet());
    }

    @Override
    public java.util.Map<Integer, Integer> getOrderCountsByStatus(Long userId) {
        List<Order> orders = orderMapper.selectList(
                new LambdaQueryWrapper<Order>()
                        .eq(Order::getUserId, userId)
                        // 仅统计子订单/独立订单，排除仅聚合支付的父订单
                        .eq(Order::getIsParent, 0)
                        .in(Order::getStatus, 0, 1, 2, 3, 4, 5, 7)
                        .select(Order::getStatus, Order::getReviewed));

        java.util.Map<Integer, Integer> counts = new java.util.HashMap<>();
        counts.put(0, 0); // 待付款
        counts.put(1, 0); // 待发货
        counts.put(2, 0); // 待收货（含部分发货）
        counts.put(3, 0); // 待评价（交易完成未评价）
        counts.put(4, 0); // 交易关闭
        counts.put(5, 0); // 退款中

        for (Order order : orders) {
            int status = order.getStatus();
            if (status == 3) {
                // 交易完成订单，只有未评价的才算"待评价"
                if (order.getReviewed() == null || order.getReviewed() == 0) {
                    counts.put(3, counts.get(3) + 1);
                }
            } else if (status == 7) {
                // 部分发货计入待收货
                counts.put(2, counts.get(2) + 1);
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
        // 只更新 reviewed 列：整行回写(orderMapper.updateById)会把读到的旧 status 一起写回，
        // 与并发的支付/退款流转互相覆盖
        orderMapper.update(null, new LambdaUpdateWrapper<Order>()
                .eq(Order::getId, order.getId())
                .set(Order::getReviewed, reviewed));
        log.info("订单评价状态更新: orderNo={}, reviewed={}", orderNo, reviewed);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int autoConfirmReceive(int expireDays) {
        // 扫描已发货(待收货)且发货时间已超过 expireDays 天仍未确认收货的订单，自动确认收货
        LocalDateTime deadline = LocalDateTime.now().minusDays(expireDays);
        List<Order> orders = orderMapper.selectList(new LambdaQueryWrapper<Order>()
                .eq(Order::getIsParent, 0)
                .eq(Order::getStatus, 2)
                .isNotNull(Order::getShipTime)
                .le(Order::getShipTime, deadline));
        int count = 0;
        for (Order order : orders) {
            try {
                Integer fromStatus = order.getStatus();
                // CAS：多实例/重跑时同一订单只被自动确认收货一次
                int rows = orderMapper.update(null, new LambdaUpdateWrapper<Order>()
                        .eq(Order::getId, order.getId())
                        .eq(Order::getStatus, 2)
                        .set(Order::getStatus, 3)
                        .set(Order::getReceiveTime, LocalDateTime.now()));
                if (rows == 0) {
                    continue;
                }
                order.setStatus(3);
                saveStatusLog(order.getId(), fromStatus, 3, "系统", "超时未确认，系统自动确认收货");
                orderEventProducer.sendOrderStatusChangeEvent(order.getOrderNo(), fromStatus, 3);
                count++;
            } catch (Exception e) {
                log.error("自动确认收货失败: orderNo={}", order.getOrderNo(), e);
            }
        }
        if (count > 0) {
            log.info("自动确认收货完成: 处理订单数={}", count);
        }
        return count;
    }

    @Override
    public List<String> listTimeoutUnpaidOrderNos(int timeoutMinutes, int limit) {
        // 对账兜底：超时取消依赖 RocketMQ 延迟消息，broker 抖动或消息进了死信时，
        // 订单会永久停在待付款且库存永久预占，此处扫出交由调用方逐个取消。
        // 只返回子订单/独立订单（父订单无明细，取消由子订单联动），且仅待付款(0)，天然幂等。
        LocalDateTime deadline = LocalDateTime.now().minusMinutes(timeoutMinutes);
        return orderMapper.selectList(new LambdaQueryWrapper<Order>()
                        .eq(Order::getIsParent, 0)
                        .eq(Order::getStatus, 0)
                        .le(Order::getCreatedAt, deadline)
                        .orderByAsc(Order::getId)
                        .last("LIMIT " + Math.max(limit, 1)))
                .stream()
                .map(Order::getOrderNo)
                .collect(Collectors.toList());
    }

    // ==================== 私有方法 ====================

    /**
     * 登记"本地事务回滚后"的远端写补偿（R4）。
     *
     * <p>createOrder 里已完成的远端写（券核销、库存预占）不会随本地事务回滚，
     * 必须显式撤回，否则券被白扣、库存被永久预占。放在事务回调而不是 catch 里，
     * 是为了同时覆盖"方法内抛异常"与"提交阶段失败"两种回滚 —— 后者发生在方法返回之后，catch 看不到。
     */
    private void registerRollbackCompensation(Runnable compensation) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return; // 非事务上下文（异常调用路径），只能依赖对账任务兜底
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != TransactionSynchronization.STATUS_COMMITTED) {
                    compensation.run();
                }
            }
        });
    }

    /**
     * 登记"本地事务提交后"执行的远端写动作。
     *
     * <p>退款这类资金写必须放在提交之后：放进事务内的话，"远端已退款 + 本地回滚"会在重试时
     * 重复退款，而资金不像库存那样可以简单回补。
     */
    private void registerAfterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run(); // 非事务上下文（异常调用路径）：直接执行，不静默丢弃资金动作
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }

    /**
     * 取消维度的退款，以子订单/独立订单为维度，refNo 固定为 {@code CANCEL:{orderNo}} 保证幂等。
     *
     * <p>拆单场景下支付单挂在父订单号上，byw-pay 的 {@code findRefundablePayOrder} 会回溯父单，
     * 并用"累计退款 ≤ 支付金额"兜底，因此逐子单退款合计恰好等于父单实付，不会漏也不会重。
     *
     * <p>现在只剩 {@link #refundCancelledOnPaid(String)} 在用：那条链路由 MQ 消费驱动，
     * 失败要抛出布尔值让消息重试，所以保留同步返回值。
     * 用户主动取消订单走的已不是这里，而是补偿任务（{@code CANCEL_REFUND}）——
     * 因为它需要"提交后执行 + 崩溃后仍能被扫到"。
     *
     * @return 退款是否成功（无需退款时视为成功）
     */
    private boolean refundForCancel(String orderNo, BigDecimal payAmount) {
        if (payAmount == null || payAmount.signum() <= 0) {
            return true;
        }
        String refNo = "CANCEL:" + orderNo;
        // 退款原因用固定文案，不拼接用户填写的取消原因：t_refund_record.reason 是 VARCHAR(200)，
        // 超长会在退款内部抛截断异常，反而让这笔退款静默失败。用户填的原因已存在 t_order.cancel_reason。
        String refundReason = "订单取消退款";
        try {
            R<Boolean> result = payFeignClient.refund(orderNo, payAmount, refundReason, refNo);
            if (result == null || !result.isSuccess() || !Boolean.TRUE.equals(result.getData())) {
                log.error("取消订单退款失败，需人工补退: orderNo={}, amount={}, refNo={}, msg={}",
                        orderNo, payAmount, refNo, result == null ? "null" : result.getMessage());
                return false;
            }
            log.info("取消订单退款成功: orderNo={}, amount={}, refNo={}", orderNo, payAmount, refNo);
            return true;
        } catch (Exception e) {
            log.error("取消订单退款异常，需人工补退: orderNo={}, amount={}, refNo={}", orderNo, payAmount, refNo, e);
            return false;
        }
    }

    /**
     * 回滚补偿：把本单核销掉的券恢复为未使用。
     *
     * <p>改为"先登记补偿任务、再立即驱动一次"：原实现在这里直接调远端、失败只打 error，
     * 进程崩在「本地回滚完成、远端调用之前」时，券就永远停留在已使用状态而无人知晓。
     * 此时业务事务已经回滚，所以用独立事务登记任务。
     */
    private void compensateReleaseCoupon(Long couponId, Long userId, String orderNo) {
        String bizKey = orderNo + ":" + couponId;
        try {
            compensationService.enqueueStandalone(CompensationType.RELEASE_COUPON, bizKey, orderNo, null,
                    new CompensationPayloads.ReleaseCoupon(couponId, userId));
        } catch (Exception e) {
            log.error("登记撤回优惠券补偿失败，券可能仍处于已使用状态，需人工处理: couponId={}, userId={}, orderNo={}",
                    couponId, userId, orderNo, e);
            return;
        }
        compensationService.executeNow(CompensationType.RELEASE_COUPON, bizKey);
    }

    /**
     * 回滚补偿：把本单预占的库存退回可售。
     *
     * <p>与撤券同理改为"登记任务 + 立即驱动"。商品侧的 releaseStock 带 CAS，
     * 重复执行不会把可售库存加两次，所以重试安全。
     */
    private void compensateReleaseStock(List<SkuStockDeductDTO> stockList, String orderNo) {
        try {
            compensationService.enqueueStandalone(CompensationType.RELEASE_STOCK, orderNo, orderNo, null,
                    new CompensationPayloads.ReleaseStock(stockList.stream()
                            .map(s -> new CompensationPayloads.ReleaseStock.Item(s.getSkuId(), s.getQuantity()))
                            .toList()));
        } catch (Exception e) {
            log.error("登记撤回库存预占补偿失败，库存可能仍被占用，需人工处理: orderNo={}, items={}",
                    orderNo, stockList, e);
            return;
        }
        compensationService.executeNow(CompensationType.RELEASE_STOCK, orderNo);
    }

    /**
     * 计算下单幂等键（R12）：同一用户 + 同一收货地址 + 同一券 + 同一备注 + 同一 SKU 数量集合，
     * 视为"同一笔下单请求"，只允许落库一次。
     *
     * <p>只取这些"用户意图"字段，不含 price/商品名等派生值 —— 它们由服务端重算，
     * 若纳入指纹反而会因展示精度差异把同一请求判成两笔。
     */
    private String buildIdempotentKey(Long userId, OrderCreateDTO createDTO, List<OrderCreateDTO.OrderItemDTO> items) {
        StringBuilder sb = new StringBuilder()
                .append(userId).append('|')
                .append(createDTO.getAddressId()).append('|')
                .append(createDTO.getCouponId()).append('|')
                .append(createDTO.getRemark());
        items.stream()
                .map(i -> i.getSkuId() + ":" + i.getQuantity())
                .sorted()
                .forEach(s -> sb.append('|').append(s));
        String hash = DigestUtils.md5DigestAsHex(sb.toString().getBytes(StandardCharsets.UTF_8));
        return "order:idem:" + userId + ":" + hash;
    }

    /** 回滚补偿：删除幂等键，让用户能带着同样内容重试（否则要干等 TTL 过期） */
    private void releaseIdempotentKey(String key) {
        try {
            redisUtil.delete(key);
        } catch (Exception e) {
            log.error("下单回滚后清理幂等键失败，需等待 TTL 自然过期: key={}", key, e);
        }
    }

    /**
     * 订单状态机：校验状态流转是否合法。终态为 交易完成(3)、交易关闭(4)。
     * 0待付款 1待发货 2待收货 3交易完成 4交易关闭 5退款中 7部分发货
     */
    private boolean isStatusTransitionAllowed(Integer from, Integer to) {
        if (from == null || to == null) {
            return false;
        }
        switch (from) {
            case 0:
                return to == 1 || to == 4;                 // 待付款 → 待发货(支付) / 交易关闭(取消)
            case 1:
                return to == 2 || to == 7 || to == 4 || to == 5; // 待发货 → 待收货 / 部分发货 / 交易关闭(取消或全部退款) / 退款中
            case 7:
                return to == 2 || to == 5 || to == 4;      // 部分发货 → 待收货 / 退款中 / 交易关闭(全部退款)
            case 2:
                return to == 3 || to == 5 || to == 4;      // 待收货 → 交易完成 / 退款中 / 交易关闭(全部退款)
            case 3:
                return to == 5;                            // 交易完成 → 退款中(售后退款)
            case 5:
                return to == 4 || to == 3;                 // 退款中 → 交易关闭(退款成功) / 交易完成(售后被拒绝或撤销回退)
            default:
                return false;                              // 交易关闭(4) 为终态
        }
    }

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
        // 雪花算法订单号：全局唯一、趋势递增，避免多实例/重启撞号
        return SnowflakeIdGenerator.nextIdStr();
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

    /**
     * 批量回填订单关联的退款类售后摘要（仅退款/退货退款），供 C 端渲染退款明细入口与寄回入口。
     * 商品级售后按 order_item_id 匹配到每条明细（各取最新一条）；
     * 订单级三字段仅由历史 order_item_id 为空的售后填充（兼容订单级退款明细入口）。
     */
    private void fillAfterSaleSummary(List<OrderDetailDTO> dtoList) {
        if (dtoList == null || dtoList.isEmpty()) {
            return;
        }
        List<String> orderNos = dtoList.stream()
                .map(OrderDetailDTO::getOrderNo)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        if (orderNos.isEmpty()) {
            return;
        }
        List<AfterSale> list = afterSaleMapper.selectList(new LambdaQueryWrapper<AfterSale>()
                .in(AfterSale::getOrderNo, orderNos)
                .in(AfterSale::getType, 1, 2)
                .orderByDesc(AfterSale::getCreatedAt));
        // 已按创建时间倒序，putIfAbsent 首个即最新
        java.util.Map<String, AfterSale> latestOrderLevel = new java.util.HashMap<>();
        java.util.Map<Long, AfterSale> latestByItem = new java.util.HashMap<>();
        for (AfterSale as : list) {
            if (as.getOrderItemId() == null) {
                latestOrderLevel.putIfAbsent(as.getOrderNo(), as);
            } else {
                latestByItem.putIfAbsent(as.getOrderItemId(), as);
            }
        }
        for (OrderDetailDTO dto : dtoList) {
            AfterSale as = latestOrderLevel.get(dto.getOrderNo());
            if (as != null) {
                dto.setAfterSaleId(as.getId());
                dto.setAfterSaleStatus(as.getStatus());
                dto.setAfterSaleType(as.getType());
            }
            if (dto.getItems() != null) {
                for (OrderDetailDTO.OrderItemDTO item : dto.getItems()) {
                    AfterSale ia = latestByItem.get(item.getId());
                    if (ia != null) {
                        item.setAfterSaleId(ia.getId());
                        item.setAfterSaleStatus(ia.getStatus());
                        item.setAfterSaleType(ia.getType());
                    }
                }
            }
        }
    }
}
