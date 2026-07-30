package com.byw.order.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.byw.api.pay.PayFeignClient;
import com.byw.api.pay.dto.RefundInfoDTO;
import com.byw.api.product.ProductFeignClient;
import com.byw.api.product.dto.SkuStockDeductDTO;
import com.byw.common.core.exception.BusinessException;
import com.byw.common.core.result.PageResult;
import com.byw.common.core.result.R;
import com.byw.common.security.context.UserContext;
import com.byw.order.dto.RefundDetailDTO;
import com.byw.order.entity.AfterSale;
import com.byw.order.entity.Order;
import com.byw.order.entity.OrderItem;
import com.byw.order.mapper.AfterSaleMapper;
import com.byw.order.mapper.OrderItemMapper;
import com.byw.order.mapper.OrderMapper;
import com.byw.order.service.AfterSaleService;
import com.byw.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AfterSaleServiceImpl implements AfterSaleService {

    private final AfterSaleMapper afterSaleMapper;
    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final PayFeignClient payFeignClient;
    private final ProductFeignClient productFeignClient;
    private final OrderService orderService;

    /** 售后单号序列计数器 */
    private static final AtomicLong SEQUENCE = new AtomicLong(0);

    /** 退款类售后类型：仅退款/退货退款/价保，必须携带退款金额 */
    private static final List<Integer> REFUND_TYPES = List.of(1, 2, 6);

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String apply(String orderNo, Long userId, Long orderItemId, Integer type, String reason, String description, BigDecimal refundAmount) {
        if (type == null || type < 1 || type > 6) {
            throw new BusinessException("售后类型不正确");
        }
        Order order = orderMapper.selectOne(new LambdaQueryWrapper<Order>().eq(Order::getOrderNo, orderNo));
        if (order == null) {
            throw new BusinessException("订单不存在");
        }
        if (!userId.equals(order.getUserId())) {
            throw new BusinessException("无权操作该订单");
        }
        // 待发货(1)/待收货(2)/部分发货(7)/交易完成(3)均可申请；订单已因其他商品售后进入退款中(5)时，其余商品仍可申请
        Integer orderStatus = order.getStatus();
        if (orderStatus == null || (orderStatus != 1 && orderStatus != 2 && orderStatus != 3
                && orderStatus != 5 && orderStatus != 7)) {
            throw new BusinessException("当前订单状态不支持申请售后");
        }
        // 商品级售后：必填明细并校验归属该订单
        if (orderItemId == null) {
            throw new BusinessException("请选择要申请售后的商品");
        }
        OrderItem item = orderItemMapper.selectById(orderItemId);
        if (item == null || !orderNo.equals(item.getOrderNo())) {
            throw new BusinessException("售后商品不属于该订单");
        }
        // 未发货商品货未寄出，不存在退货/换货/维修/补寄，仅支持仅退款与价保
        boolean itemShipped = item.getShipStatus() != null && item.getShipStatus() == 1;
        if (!itemShipped && type != 1 && type != 6) {
            throw new BusinessException("该商品尚未发货，仅支持申请仅退款或价保");
        }
        // 同一商品存在进行中的售后单时不允许重复申请（待审核0/待寄回1/待收货5/退款中6）；被拒/撤销可重新申请
        Long processing = afterSaleMapper.selectCount(new LambdaQueryWrapper<AfterSale>()
                .eq(AfterSale::getOrderItemId, orderItemId)
                .in(AfterSale::getStatus, 0, 1, 5, 6));
        if (processing != null && processing > 0) {
            throw new BusinessException("该商品已有进行中的售后申请，请勿重复提交");
        }
        // 已完成退款的商品不可再次申请退款类售后
        Long refunded = afterSaleMapper.selectCount(new LambdaQueryWrapper<AfterSale>()
                .eq(AfterSale::getOrderItemId, orderItemId)
                .in(AfterSale::getType, 1, 2)
                .eq(AfterSale::getStatus, 3));
        if (refunded != null && refunded > 0) {
            throw new BusinessException("该商品已完成退款");
        }
        // 退款类售后校验金额：必填且不能超过该商品明细小计
        if (REFUND_TYPES.contains(type)) {
            if (refundAmount == null || refundAmount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException("请填写退款金额");
            }
            if (item.getSubtotal() != null && refundAmount.compareTo(item.getSubtotal()) > 0) {
                throw new BusinessException("退款金额不能超过该商品实付小计");
            }
        } else {
            refundAmount = null;
        }

        AfterSale afterSale = new AfterSale();
        afterSale.setAfterSaleNo(generateAfterSaleNo());
        afterSale.setOrderNo(orderNo);
        afterSale.setOrderItemId(orderItemId);
        afterSale.setProductName(item.getProductName());
        afterSale.setSkuName(item.getSkuName());
        afterSale.setProductImage(item.getProductImage());
        afterSale.setUserId(userId);
        afterSale.setShopId(order.getShopId());
        afterSale.setType(type);
        afterSale.setReason(reason);
        afterSale.setDescription(description);
        afterSale.setRefundAmount(refundAmount);
        afterSale.setStatus(0);
        afterSaleMapper.insert(afterSale);

        // 退款类售后（仅退款/退货退款）：交易完成的订单同步进入「退款中」(5)，被拒/撤销后回退为交易完成；
        // 确认收货前（1/2/7）申请售后订单主状态不变，售后进度仅体现在商品行上，避免阻塞其余商品发货
        if ((type == 1 || type == 2) && order.getStatus() == 3) {
            orderService.updateStatus(orderNo, 5);
        }
        log.info("售后申请提交成功: afterSaleNo={}, orderNo={}, orderItemId={}, userId={}, type={}",
                afterSale.getAfterSaleNo(), orderNo, orderItemId, userId, type);
        return afterSale.getAfterSaleNo();
    }

    @Override
    public PageResult<AfterSale> getUserAfterSales(Long userId, Integer pageNum, Integer pageSize) {
        Page<AfterSale> page = afterSaleMapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<AfterSale>()
                        .eq(AfterSale::getUserId, userId)
                        .orderByDesc(AfterSale::getCreatedAt));
        return PageResult.of(page.getRecords(), page.getTotal(), pageNum, pageSize);
    }

    @Override
    public AfterSale getActiveByOrderNo(String orderNo) {
        return afterSaleMapper.selectOne(new LambdaQueryWrapper<AfterSale>()
                .eq(AfterSale::getOrderNo, orderNo)
                .in(AfterSale::getStatus, 0, 1, 5, 6)
                .orderByDesc(AfterSale::getCreatedAt)
                .last("LIMIT 1"));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long id, Long userId) {
        AfterSale afterSale = afterSaleMapper.selectById(id);
        if (afterSale == null) {
            throw new BusinessException("售后单不存在");
        }
        if (!userId.equals(afterSale.getUserId())) {
            throw new BusinessException("无权操作该售后单");
        }
        if (afterSale.getStatus() != 0) {
            throw new BusinessException("仅待审核的售后单可以撤销");
        }
        afterSale.setStatus(4);
        afterSaleMapper.updateById(afterSale);
        revertOrderRefunding(afterSale);
    }

    @Override
    public PageResult<AfterSale> getShopAfterSales(Integer status, Integer pageNum, Integer pageSize) {
        LambdaQueryWrapper<AfterSale> wrapper = new LambdaQueryWrapper<AfterSale>()
                .orderByDesc(AfterSale::getCreatedAt);
        // 商家上下文：仅返回本店售后单；平台管理员 shopId 为空则不过滤
        Long shopId = UserContext.getShopId();
        wrapper.eq(shopId != null, AfterSale::getShopId, shopId);
        if (status != null) {
            wrapper.eq(AfterSale::getStatus, status);
        }
        Page<AfterSale> page = afterSaleMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        return PageResult.of(page.getRecords(), page.getTotal(), pageNum, pageSize);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approve(Long id) {
        AfterSale afterSale = requireShopAfterSale(id);
        if (afterSale.getStatus() == null || afterSale.getStatus() != 0) {
            throw new BusinessException("仅待审核的售后单可以审核");
        }
        afterSale.setApproveTime(LocalDateTime.now());
        Integer type = afterSale.getType();
        if (type != null && type == 1) {
            // 仅退款：审核通过即退款
            afterSaleMapper.updateById(afterSale);
            doRefund(afterSale);
        } else if (type != null && type == 2) {
            // 退货退款：审核通过转「待买家寄回」
            afterSale.setStatus(1);
            afterSaleMapper.updateById(afterSale);
        } else {
            // 换货/维修/补寄/价保：审核同意即完成，不触发关单退款
            afterSale.setStatus(3);
            afterSale.setFinishTime(LocalDateTime.now());
            afterSaleMapper.updateById(afterSale);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reject(Long id, String reason) {
        AfterSale afterSale = requireShopAfterSale(id);
        if (afterSale.getStatus() == null || afterSale.getStatus() != 0) {
            throw new BusinessException("仅待审核的售后单可以拒绝");
        }
        afterSale.setStatus(2);
        afterSale.setRejectReason(reason);
        afterSaleMapper.updateById(afterSale);
        revertOrderRefunding(afterSale);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void fillReturnShipping(Long id, Long userId, String company, String trackingNo) {
        AfterSale afterSale = afterSaleMapper.selectById(id);
        if (afterSale == null) {
            throw new BusinessException("售后单不存在");
        }
        if (!userId.equals(afterSale.getUserId())) {
            throw new BusinessException("无权操作该售后单");
        }
        if (afterSale.getStatus() == null || afterSale.getStatus() != 1) {
            throw new BusinessException("当前售后单状态不可填写寄回信息");
        }
        if (trackingNo == null || trackingNo.trim().isEmpty()) {
            throw new BusinessException("请填写寄回运单号");
        }
        afterSale.setReturnCompany(company);
        afterSale.setReturnTrackingNo(trackingNo);
        afterSale.setReturnShipTime(LocalDateTime.now());
        afterSale.setStatus(5); // 待商家收货
        afterSaleMapper.updateById(afterSale);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void confirmReturnReceived(Long id) {
        AfterSale afterSale = requireShopAfterSale(id);
        if (afterSale.getStatus() == null || afterSale.getStatus() != 5) {
            throw new BusinessException("仅待商家收货的售后单可以确认收货");
        }
        afterSale.setReceiveTime(LocalDateTime.now());
        afterSaleMapper.updateById(afterSale);
        doRefund(afterSale);
    }

    @Override
    public RefundDetailDTO getRefundDetail(String orderNo, Long userId, Long itemId) {
        AfterSale afterSale = afterSaleMapper.selectOne(new LambdaQueryWrapper<AfterSale>()
                .eq(AfterSale::getOrderNo, orderNo)
                .in(AfterSale::getType, 1, 2)
                // itemId 非空时定位商品级售后；为空取最新一条（兼容历史订单级售后）
                .eq(itemId != null, AfterSale::getOrderItemId, itemId)
                .orderByDesc(AfterSale::getCreatedAt)
                .last("LIMIT 1"));
        if (afterSale == null) {
            throw new BusinessException("该订单暂无退款记录");
        }
        if (!userId.equals(afterSale.getUserId())) {
            throw new BusinessException("无权查看该退款明细");
        }

        RefundDetailDTO dto = new RefundDetailDTO();
        dto.setAfterSaleId(afterSale.getId());
        dto.setAfterSaleNo(afterSale.getAfterSaleNo());
        dto.setOrderNo(afterSale.getOrderNo());
        dto.setProductName(afterSale.getProductName());
        dto.setSkuName(afterSale.getSkuName());
        dto.setType(afterSale.getType());
        dto.setStatus(afterSale.getStatus());
        dto.setRefundAmount(afterSale.getRefundAmount());
        dto.setRejectReason(afterSale.getRejectReason());

        // 退款到账信息：退款发起后可从支付服务查询
        try {
            R<RefundInfoDTO> refundResult = payFeignClient.getRefundByOrderNo(orderNo);
            if (refundResult != null && refundResult.isSuccess() && refundResult.getData() != null) {
                RefundInfoDTO refund = refundResult.getData();
                dto.setRefundNo(refund.getRefundNo());
                dto.setRefundStatus(refund.getStatus());
                dto.setPayChannel(refund.getPayChannel());
                dto.setRefundCreatedAt(refund.getCreatedAt());
                dto.setRefundUpdatedAt(refund.getUpdatedAt());
            }
        } catch (Exception e) {
            log.warn("查询退款到账信息失败: orderNo={}, err={}", orderNo, e.getMessage());
        }

        dto.setTimeline(buildTimeline(afterSale, dto));
        return dto;
    }

    /** 构建退款流程时间线：申请 → 审核 → (寄回 → 收货) → 退款中 → 退款成功 */
    private List<RefundDetailDTO.TimelineNode> buildTimeline(AfterSale afterSale, RefundDetailDTO dto) {
        List<RefundDetailDTO.TimelineNode> nodes = new ArrayList<>();
        Integer status = afterSale.getStatus();
        boolean rejected = status != null && status == 2;
        boolean finished = status != null && status == 3;

        nodes.add(new RefundDetailDTO.TimelineNode("提交申请", afterSale.getCreatedAt(), true));
        if (rejected) {
            nodes.add(new RefundDetailDTO.TimelineNode("商家已拒绝", afterSale.getApproveTime(), true));
            return nodes;
        }
        nodes.add(new RefundDetailDTO.TimelineNode("商家审核通过", afterSale.getApproveTime(),
                afterSale.getApproveTime() != null));

        if (afterSale.getType() != null && afterSale.getType() == 2) {
            nodes.add(new RefundDetailDTO.TimelineNode("买家寄回", afterSale.getReturnShipTime(),
                    afterSale.getReturnShipTime() != null));
            nodes.add(new RefundDetailDTO.TimelineNode("商家确认收货", afterSale.getReceiveTime(),
                    afterSale.getReceiveTime() != null));
        }

        boolean refundStarted = dto.getRefundNo() != null || (status != null && (status == 6 || status == 3));
        LocalDateTime refundStartTime = dto.getRefundCreatedAt();
        nodes.add(new RefundDetailDTO.TimelineNode("退款处理中", refundStartTime, refundStarted));

        boolean refundSuccess = finished || (dto.getRefundStatus() != null && dto.getRefundStatus() == 1);
        LocalDateTime refundDoneTime = refundSuccess
                ? (dto.getRefundUpdatedAt() != null ? dto.getRefundUpdatedAt() : afterSale.getFinishTime())
                : null;
        nodes.add(new RefundDetailDTO.TimelineNode("退款成功", refundDoneTime, refundSuccess));
        return nodes;
    }

    /** 执行退款：置退款中 → 调支付服务原路退回 → 置已完成，并编排订单状态流转 */
    private void doRefund(AfterSale afterSale) {
        afterSale.setStatus(6); // 退款中
        afterSaleMapper.updateById(afterSale);

        BigDecimal amount = afterSale.getRefundAmount();
        R<Boolean> refundResult = payFeignClient.refund(afterSale.getOrderNo(), amount,
                afterSale.getReason() == null ? "售后退款" : afterSale.getReason());
        if (refundResult == null || !refundResult.isSuccess() || !Boolean.TRUE.equals(refundResult.getData())) {
            log.error("售后退款失败: afterSaleNo={}, orderNo={}, amount={}, respCode={}, respMessage={}",
                    afterSale.getAfterSaleNo(), afterSale.getOrderNo(), amount,
                    refundResult == null ? null : refundResult.getCode(),
                    refundResult == null ? null : refundResult.getMessage());
            // 透传支付服务的失败原因（如"未找到已支付的订单"），便于定位问题
            String message = refundResult == null ? null : refundResult.getMessage();
            throw new BusinessException(message == null || message.isEmpty() ? "退款失败，请稍后重试" : message);
        }

        afterSale.setStatus(3); // 已完成
        afterSale.setFinishTime(LocalDateTime.now());
        afterSaleMapper.updateById(afterSale);
        // 未发货商品仅退款成功：货未寄出，回补下单时扣减的库存
        releaseStockIfUnshipped(afterSale);
        // 退款成功后的订单状态编排：全部退完关单 / 部分退款回退交易完成 / 仍有进行中保持退款中
        settleOrderAfterRefund(afterSale);
        log.info("售后退款完成: afterSaleNo={}, orderNo={}, amount={}",
                afterSale.getAfterSaleNo(), afterSale.getOrderNo(), amount);
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

    /** 未发货商品仅退款成功：回补下单时扣减的库存（退货退款的商品已寄出，不自动回补） */
    private void releaseStockIfUnshipped(AfterSale afterSale) {
        if (afterSale.getOrderItemId() == null || afterSale.getType() == null || afterSale.getType() != 1) {
            return;
        }
        OrderItem item = orderItemMapper.selectById(afterSale.getOrderItemId());
        if (item == null || (item.getShipStatus() != null && item.getShipStatus() == 1)) {
            return;
        }
        try {
            productFeignClient.releaseStock(List.of(new SkuStockDeductDTO(item.getSkuId(), item.getQuantity())));
            log.info("未发货商品退款回补库存: afterSaleNo={}, skuId={}, quantity={}",
                    afterSale.getAfterSaleNo(), item.getSkuId(), item.getQuantity());
        } catch (Exception e) {
            // 库存回补失败不阻断退款主流程，记日志人工处理
            log.warn("未发货商品退款回补库存失败: afterSaleNo={}, skuId={}, error={}",
                    afterSale.getAfterSaleNo(), item.getSkuId(), e.getMessage());
        }
    }

    /** 售后被拒绝/撤销：订单不再有其他进行中的退款类售后时，从「退款中」(5)回退为「交易完成」(3) */
    private void revertOrderRefunding(AfterSale afterSale) {
        Integer type = afterSale.getType();
        if (type == null || (type != 1 && type != 2)) {
            return;
        }
        Long processing = afterSaleMapper.selectCount(new LambdaQueryWrapper<AfterSale>()
                .eq(AfterSale::getOrderNo, afterSale.getOrderNo())
                .in(AfterSale::getType, 1, 2)
                .in(AfterSale::getStatus, 0, 1, 5, 6));
        if (processing != null && processing > 0) {
            return;
        }
        Order order = orderMapper.selectOne(new LambdaQueryWrapper<Order>()
                .eq(Order::getOrderNo, afterSale.getOrderNo()));
        if (order != null && order.getStatus() != null && order.getStatus() == 5) {
            orderService.updateStatus(afterSale.getOrderNo(), 3);
        }
    }

    /** 加载售后单并校验商家本店归属（平台管理员 shopId 为空则跳过校验） */
    private AfterSale requireShopAfterSale(Long id) {
        AfterSale afterSale = afterSaleMapper.selectById(id);
        if (afterSale == null) {
            throw new BusinessException("售后单不存在");
        }
        Long shopId = UserContext.getShopId();
        if (shopId != null && !shopId.equals(afterSale.getShopId())) {
            throw new BusinessException("无权操作该售后单");
        }
        return afterSale;
    }

    /** 生成售后单号：AS + 时间戳 + 5位序列 */
    private String generateAfterSaleNo() {
        String datePart = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        long seq = SEQUENCE.incrementAndGet() % 100000;
        return "AS" + datePart + String.format("%05d", seq);
    }
}
