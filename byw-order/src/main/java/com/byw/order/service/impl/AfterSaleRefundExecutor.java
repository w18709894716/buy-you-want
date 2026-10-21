package com.byw.order.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.byw.api.product.ProductFeignClient;
import com.byw.api.product.dto.SkuStockDeductDTO;
import com.byw.order.entity.AfterSale;
import com.byw.order.entity.Order;
import com.byw.order.entity.OrderItem;
import com.byw.order.mapper.AfterSaleMapper;
import com.byw.order.mapper.OrderItemMapper;
import com.byw.order.mapper.OrderMapper;
import com.byw.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 售后退款"落终态"事务单元。
 *
 * <p>独立 Bean 保证 {@code @Transactional} 经代理生效（避免同类自调用失效），与 byw-settle 的
 * {@code SettleAccounting} 同构。
 *
 * <p>职责边界：本类只做**本地可回滚**的部分 —— 售后单 6→3 + 订单状态编排。
 * 远程退款与库存回补都在事务之外（见 {@code AfterSaleServiceImpl.driveRefundIfPending}），
 * 避免"远程已成功、本地回滚"导致的不一致与重试放大。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AfterSaleRefundExecutor {

    /** 秒杀订单标识：库存在 promotion 服务的 Redis 侧预扣，从不写 product 库，售后不回补 DB 库存 */
    private static final String SECKILL_ORDER_REMARK = "秒杀活动订单";

    private final AfterSaleMapper afterSaleMapper;
    private final OrderItemMapper orderItemMapper;
    private final OrderMapper orderMapper;
    private final ProductFeignClient productFeignClient;
    private final OrderService orderService;

    /**
     * 退款成功后落终态：售后单 退款中(6) → 已完成(3)，并编排订单状态。
     *
     * <p>用 CAS（WHERE status = 6）更新，重复驱动只有一次返回 true，天然幂等。
     * 订单编排抛错会让整个事务回滚（售后单退回"退款中"），由对账任务重试收敛 —— 这是刻意的：
     * 退款已经发生，终态必须收敛，宁可重试也不能静默停在中间态。
     *
     * @return 本次是否真正完成了状态流转
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean finalizeRefund(Long afterSaleId) {
        AfterSale afterSale = afterSaleMapper.selectById(afterSaleId);
        if (afterSale == null) {
            return false;
        }
        if (afterSale.getStatus() != null && afterSale.getStatus() == 3) {
            return false;
        }
        AfterSale update = new AfterSale();
        update.setId(afterSaleId);
        update.setStatus(3);
        update.setFinishTime(LocalDateTime.now());
        int rows = afterSaleMapper.update(update, new LambdaQueryWrapper<AfterSale>()
                .eq(AfterSale::getId, afterSaleId)
                .eq(AfterSale::getStatus, 6));
        if (rows == 0) {
            log.info("售后单已不在退款中，跳过终态流转: afterSaleId={}", afterSaleId);
            return false;
        }
        afterSale.setStatus(3);
        settleOrderAfterRefund(afterSale);
        log.info("售后退款完成: afterSaleNo={}, orderNo={}", afterSale.getAfterSaleNo(), afterSale.getOrderNo());
        return true;
    }

    /**
     * 未发货商品仅退款成功后的库存回补：在终态事务**提交之后**调用，best-effort。
     *
     * <p>刻意放在事务外：若放进事务内，"远程回补成功 + 本地回滚"会在重试时重复回补（库存虚增）。
     * 漏补只是少卖且已打 warn，可由人工/对账补；宁可少补，不可多补。
     */
    public void restoreStockIfUnshipped(AfterSale afterSale) {
        if (afterSale == null || afterSale.getOrderItemId() == null
                || afterSale.getType() == null || afterSale.getType() != 1) {
            return;
        }
        OrderItem item = orderItemMapper.selectById(afterSale.getOrderItemId());
        if (item == null || (item.getShipStatus() != null && item.getShipStatus() == 1)) {
            return;
        }
        Order order = orderMapper.selectOne(new LambdaQueryWrapper<Order>()
                .eq(Order::getOrderNo, afterSale.getOrderNo()));
        if (order != null && SECKILL_ORDER_REMARK.equals(order.getRemark())) {
            log.info("秒杀订单售后跳过DB库存回补（Redis侧管理）: afterSaleNo={}", afterSale.getAfterSaleNo());
            return;
        }
        try {
            productFeignClient.restoreStock(List.of(new SkuStockDeductDTO(item.getSkuId(), item.getQuantity())));
            log.info("未发货商品退款回补库存: afterSaleNo={}, skuId={}, quantity={}",
                    afterSale.getAfterSaleNo(), item.getSkuId(), item.getQuantity());
        } catch (Exception e) {
            log.warn("未发货商品退款回补库存失败（需人工补），afterSaleNo={}, skuId={}, error={}",
                    afterSale.getAfterSaleNo(), item.getSkuId(), e.getMessage());
        }
    }

    /**
     * 退款成功后的订单状态编排：
     * 历史订单级售后（orderItemId 为空）→ 整单退款，订单置交易关闭(4)；
     * 商品级售后 → 全部明细均已完成退款则关单(4)（含确认收货前 1/2/7 场景）；
     * 部分退款时仅退款中(5)的订单需回退：无其他进行中退款售后回退交易完成(3)，仍有则保持退款中；
     * 确认收货前（1/2/7）部分退款主状态未动，无需处理
     */
    private void settleOrderAfterRefund(AfterSale afterSale) {
        String orderNo = afterSale.getOrderNo();
        Order order = orderMapper.selectOne(new LambdaQueryWrapper<Order>().eq(Order::getOrderNo, orderNo));
        if (order == null || order.getStatus() == null) {
            return;
        }
        Integer status = order.getStatus();
        if (afterSale.getOrderItemId() == null) {
            // 历史订单级售后：整单退款 → 交易关闭（退款驱动关单自动标记 close_type=2）
            if (status == 5) {
                orderService.updateStatus(orderNo, 4);
            }
            return;
        }
        // 全部明细均已存在已完成的退款类售后 → 整单退完，交易关闭
        List<OrderItem> items = orderItemMapper.selectList(new LambdaQueryWrapper<OrderItem>()
                .eq(OrderItem::getOrderNo, orderNo));
        Set<Long> refundedItemIds = afterSaleMapper.selectList(new LambdaQueryWrapper<AfterSale>()
                        .eq(AfterSale::getOrderNo, orderNo)
                        .in(AfterSale::getType, 1, 2)
                        .eq(AfterSale::getStatus, 3)
                        .isNotNull(AfterSale::getOrderItemId)).stream()
                .map(AfterSale::getOrderItemId)
                .collect(Collectors.toSet());
        boolean allRefunded = !items.isEmpty()
                && items.stream().allMatch(it -> refundedItemIds.contains(it.getId()));
        if (allRefunded) {
            if (status == 5 || status == 1 || status == 2 || status == 7) {
                orderService.updateStatus(orderNo, 4);
            }
            return;
        }
        // 部分退款：部分发货(7)时若剩余未退款商品已全部发货，推进待收货(2)，避免订单卡在部分发货等一个不会再发的商品
        if (status == 1 || status == 7) {
            boolean restAllShipped = items.stream()
                    .allMatch(it -> refundedItemIds.contains(it.getId())
                            || (it.getShipStatus() != null && it.getShipStatus() == 1));
            if (restAllShipped) {
                orderService.updateStatus(orderNo, 2);
            }
            return;
        }
        // 部分退款：仅交易完成路径（退款中5）需要回退；待收货(2)主状态未动，保持原状态继续履约
        if (status != 5) {
            return;
        }
        Long processing = afterSaleMapper.selectCount(new LambdaQueryWrapper<AfterSale>()
                .eq(AfterSale::getOrderNo, orderNo)
                .in(AfterSale::getType, 1, 2)
                .in(AfterSale::getStatus, 0, 1, 5, 6));
        if (processing == null || processing == 0) {
            orderService.updateStatus(orderNo, 3);
        }
    }
}
