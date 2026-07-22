package com.byw.order.consumer;

import com.byw.api.product.ProductFeignClient;
import com.byw.api.product.dto.ProductDTO;
import com.byw.api.product.dto.SkuDTO;
import com.byw.api.user.UserFeignClient;
import com.byw.api.user.dto.AddressDTO;
import com.byw.common.core.result.R;
import com.byw.common.rocketmq.constant.RocketMQTopics;
import com.byw.order.entity.Order;
import com.byw.order.entity.OrderItem;
import com.byw.order.mapper.OrderItemMapper;
import com.byw.order.mapper.OrderMapper;
import com.byw.order.mapper.OrderStatusLogMapper;
import com.byw.order.entity.OrderStatusLog;
import com.byw.order.producer.OrderEventProducer;
import com.byw.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 秒杀订单消费者
 * 消费 promotion 服务发送的 seckill-order 消息，在 t_order + t_order_item 中创建真实订单
 *
 * 注意：秒杀已在 promotion 服务的 Redis 中预扣库存，此处不再调用 deductStock
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = RocketMQTopics.SECKILL_ORDER,
        consumerGroup = "byw-order-seckill-group"
)
public class SeckillOrderConsumer implements RocketMQListener<Map<String, Object>> {

    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final OrderStatusLogMapper orderStatusLogMapper;
    private final ProductFeignClient productFeignClient;
    private final UserFeignClient userFeignClient;
    private final OrderService orderService;
    private final OrderEventProducer orderEventProducer;

    @Override
    public void onMessage(Map<String, Object> message) {
        try {
            Long activityId = toLong(message.get("activityId"));
            Long itemId = toLong(message.get("itemId")); // 多商品活动的商品条目ID（旧消息可能为空）
            Long userId = toLong(message.get("userId"));
            Long productId = toLong(message.get("productId"));
            Long skuId = toLong(message.get("skuId"));
            Long addressId = toLong(message.get("addressId")); // 用户指定的收货地址（旧消息或未选择时为空）
            BigDecimal seckillPrice = new BigDecimal(String.valueOf(message.get("seckillPrice")));

            log.info("收到秒杀订单消息: activityId={}, itemId={}, userId={}, productId={}, skuId={}, addressId={}, price={}",
                    activityId, itemId, userId, productId, skuId, addressId, seckillPrice);

            // 1. 获取商品信息
            String productName = "秒杀商品";
            String productImage = "";
            String skuName = "";

            try {
                R<ProductDTO> productResult = productFeignClient.getProductById(productId);
                if (productResult.isSuccess() && productResult.getData() != null) {
                    ProductDTO product = productResult.getData();
                    productName = product.getName();
                    productImage = product.getMainImage() != null ? product.getMainImage() : "";

                    // 从 SKU 列表中查找对应 SKU 名称
                    if (product.getSkus() != null && skuId != null && skuId > 0) {
                        for (SkuDTO sku : product.getSkus()) {
                            if (skuId.equals(sku.getId())) {
                                skuName = sku.getSkuName() != null ? sku.getSkuName() : "";
                                break;
                            }
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("获取商品信息失败, productId={}: {}", productId, e.getMessage());
            }

            // 2. 获取收货地址：优先用用户抢购时选择的地址，否则回退默认地址
            String receiverName = null;
            String receiverPhone = null;
            String receiverAddress = null;
            try {
                AddressDTO addr = null;
                if (addressId != null) {
                    R<AddressDTO> addrResult = userFeignClient.getAddressById(addressId);
                    if (addrResult.isSuccess() && addrResult.getData() != null
                            && userId.equals(addrResult.getData().getUserId())) {
                        addr = addrResult.getData();
                    }
                }
                // 未传地址或地址无效时，回退默认地址
                if (addr == null) {
                    R<AddressDTO> defaultResult = userFeignClient.getDefaultAddress(userId);
                    if (defaultResult.isSuccess() && defaultResult.getData() != null) {
                        addr = defaultResult.getData();
                    }
                }
                if (addr != null) {
                    receiverName = addr.getReceiverName();
                    receiverPhone = addr.getReceiverPhone();
                    receiverAddress = (addr.getProvince() != null ? addr.getProvince() : "")
                            + (addr.getCity() != null ? addr.getCity() : "")
                            + (addr.getDistrict() != null ? addr.getDistrict() : "")
                            + (addr.getDetailAddress() != null ? addr.getDetailAddress() : "");
                }
            } catch (Exception e) {
                log.warn("获取收货地址失败, userId={}, addressId={}: {}", userId, addressId, e.getMessage());
            }

            // 3. 生成订单号
            String orderNo = orderService.generateOrderNo();

            // 4. 创建订单记录
            Order order = new Order();
            order.setOrderNo(orderNo);
            order.setUserId(userId);
            order.setTotalAmount(seckillPrice);
            order.setPayAmount(seckillPrice);
            order.setFreightAmount(BigDecimal.ZERO);
            order.setDiscountAmount(BigDecimal.ZERO);
            order.setStatus(0); // 待付款
            order.setReviewed(0);
            order.setRemark("秒杀活动订单");
            order.setReceiverName(receiverName);
            order.setReceiverPhone(receiverPhone);
            order.setReceiverAddress(receiverAddress);
            orderMapper.insert(order);

            // 5. 创建订单项记录
            OrderItem orderItem = new OrderItem();
            orderItem.setOrderId(order.getId());
            orderItem.setOrderNo(orderNo);
            orderItem.setUserId(userId);
            orderItem.setProductId(productId);
            orderItem.setSkuId(skuId);
            orderItem.setProductName(productName);
            orderItem.setSkuName(skuName);
            orderItem.setProductImage(productImage);
            orderItem.setPrice(seckillPrice);
            orderItem.setQuantity(1);
            orderItem.setSubtotal(seckillPrice);
            orderItemMapper.insert(orderItem);

            // 6. 发送30分钟超时取消延迟消息
            orderEventProducer.sendOrderTimeoutCancelEvent(orderNo);

            // 7. 保存状态日志
            OrderStatusLog statusLog = new OrderStatusLog();
            statusLog.setOrderId(order.getId());
            statusLog.setFromStatus(null);
            statusLog.setToStatus(0);
            statusLog.setOperator("系统");
            statusLog.setRemark("秒杀订单创建");
            orderStatusLogMapper.insert(statusLog);

            log.info("秒杀订单创建成功: orderNo={}, userId={}, activityId={}, itemId={}", orderNo, userId, activityId, itemId);
        } catch (Exception e) {
            log.error("处理秒杀订单消息失败: {}", message, e);
            throw e; // 重新抛出，让 RocketMQ 重试
        }
    }

    private Long toLong(Object obj) {
        if (obj == null) return null;
        if (obj instanceof Number) return ((Number) obj).longValue();
        return Long.parseLong(String.valueOf(obj));
    }
}
