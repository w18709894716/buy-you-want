package com.byw.order.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.byw.api.pay.PayFeignClient;
import com.byw.api.pay.dto.RefundInfoDTO;
import com.byw.common.core.exception.BusinessException;
import com.byw.common.core.util.SnowflakeIdGenerator;
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
import com.byw.order.service.CompensationService;
import com.byw.order.service.OrderService;
import com.byw.order.service.compensation.CompensationType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AfterSaleServiceImpl implements AfterSaleService {

    private final AfterSaleMapper afterSaleMapper;
    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final PayFeignClient payFeignClient;
    private final OrderService orderService;
    private final AfterSaleRefundExecutor refundExecutor;
    private final CompensationService compensationService;

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
        // 退款类售后校验金额：必填，且不得超过该明细的可退上限
        if (REFUND_TYPES.contains(type)) {
            if (refundAmount == null || refundAmount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException("请填写退款金额");
            }
            BigDecimal maxRefundable = calcMaxRefundable(order, item);
            if (refundAmount.compareTo(maxRefundable) > 0) {
                log.warn("退款金额超出可退上限: orderNo={}, orderItemId={}, applyAmount={}, maxRefundable={}",
                        orderNo, orderItemId, refundAmount, maxRefundable);
                throw new BusinessException("退款金额不能超过该商品实付金额");
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
        try {
            afterSaleMapper.insert(afterSale);
        } catch (DuplicateKeyException e) {
            // 并发重复提交兜底：同一订单明细同时只允许一条"进行中"售后单（唯一键 uk_item_active）。
            // 上面的 selectCount 检查在并发下会双双通过，所以必须处理冲突而不是只靠预检查。
            throw new BusinessException("该商品已有进行中的售后申请，请勿重复提交");
        }

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
        // CAS：仅待审核(0)可撤销，避免与商家审核并发时互相覆盖
        AfterSale update = new AfterSale();
        update.setId(id);
        update.setStatus(4);
        int rows = afterSaleMapper.update(update, new LambdaQueryWrapper<AfterSale>()
                .eq(AfterSale::getId, id)
                .eq(AfterSale::getStatus, 0));
        if (rows == 0) {
            throw new BusinessException("售后单状态已变更，请刷新后重试");
        }
        afterSale.setStatus(4);
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
        LocalDateTime now = LocalDateTime.now();
        Integer type = afterSale.getType();
        AfterSale update = new AfterSale();
        update.setId(id);
        update.setApproveTime(now);
        if (type != null && (type == 1 || type == 6)) {
            // 仅退款 / 价保：都是纯退款、买家无需寄回，先置「退款中」并提交事务，
            // 远程退款由 driveRefundIfPending 在事务外驱动，
            // 避免"远程已退款、本地回滚"造成钱退了但售后单回到待审核。
            // 价保此前落在这里之外的 else 分支（审核同意即"已完成"），钱一分都没退。
            update.setStatus(6);
        } else if (type != null && type == 2) {
            // 退货退款：审核通过转「待买家寄回」
            update.setStatus(1);
        } else {
            // 换货/维修/补寄：审核同意即完成，不涉及资金，不触发退款
            update.setStatus(3);
            update.setFinishTime(now);
        }
        // CAS：仅待审核(0)可流转，并发审核/撤销只有一个能生效（退款只可能被触发一次）
        int rows = afterSaleMapper.update(update, new LambdaQueryWrapper<AfterSale>()
                .eq(AfterSale::getId, id)
                .eq(AfterSale::getStatus, 0));
        if (rows == 0) {
            throw new BusinessException("售后单状态已变更，请刷新后重试");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reject(Long id, String reason) {
        AfterSale afterSale = requireShopAfterSale(id);
        if (afterSale.getStatus() == null || afterSale.getStatus() != 0) {
            throw new BusinessException("仅待审核的售后单可以拒绝");
        }
        AfterSale update = new AfterSale();
        update.setId(id);
        update.setStatus(2);
        update.setRejectReason(reason);
        int rows = afterSaleMapper.update(update, new LambdaQueryWrapper<AfterSale>()
                .eq(AfterSale::getId, id)
                .eq(AfterSale::getStatus, 0));
        if (rows == 0) {
            throw new BusinessException("售后单状态已变更，请刷新后重试");
        }
        afterSale.setStatus(2);
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
        AfterSale update = new AfterSale();
        update.setId(id);
        update.setReturnCompany(company);
        update.setReturnTrackingNo(trackingNo);
        update.setReturnShipTime(LocalDateTime.now());
        update.setStatus(5); // 待商家收货
        int rows = afterSaleMapper.update(update, new LambdaQueryWrapper<AfterSale>()
                .eq(AfterSale::getId, id)
                .eq(AfterSale::getStatus, 1));
        if (rows == 0) {
            throw new BusinessException("售后单状态已变更，请刷新后重试");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void confirmReturnReceived(Long id) {
        AfterSale afterSale = requireShopAfterSale(id);
        if (afterSale.getStatus() == null || afterSale.getStatus() != 5) {
            throw new BusinessException("仅待商家收货的售后单可以确认收货");
        }
        AfterSale update = new AfterSale();
        update.setId(id);
        update.setReceiveTime(LocalDateTime.now());
        update.setStatus(6); // 退款中：远程退款由事务外驱动
        int rows = afterSaleMapper.update(update, new LambdaQueryWrapper<AfterSale>()
                .eq(AfterSale::getId, id)
                .eq(AfterSale::getStatus, 5));
        if (rows == 0) {
            throw new BusinessException("售后单状态已变更，请刷新后重试");
        }
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

    @Override
    public void driveRefundIfPending(Long id) {
        AfterSale afterSale = afterSaleMapper.selectById(id);
        if (afterSale == null || afterSale.getStatus() == null || afterSale.getStatus() != 6) {
            return; // 不在「退款中」无需处理（审核拒绝、退货待寄回、其他售后类型等）
        }
        BigDecimal amount = afterSale.getRefundAmount();
        // 远程退款在本地事务之外调用，且以 afterSaleNo 作为幂等业务键：
        // 重复驱动（对账任务、重试）不会重复退款。
        R<Boolean> refundResult = payFeignClient.refund(afterSale.getOrderNo(), amount,
                afterSale.getReason() == null ? "售后退款" : afterSale.getReason(),
                afterSale.getAfterSaleNo());
        if (refundResult == null || !refundResult.isSuccess() || !Boolean.TRUE.equals(refundResult.getData())) {
            log.error("售后退款失败: afterSaleNo={}, orderNo={}, amount={}, respCode={}, respMessage={}",
                    afterSale.getAfterSaleNo(), afterSale.getOrderNo(), amount,
                    refundResult == null ? null : refundResult.getCode(),
                    refundResult == null ? null : refundResult.getMessage());
            // 透传支付服务的失败原因（如"未找到已支付的订单"），便于定位问题；
            // 售后单保持「退款中」，由 AfterSaleRefundScheduler 对账重试
            String message = refundResult == null ? null : refundResult.getMessage();
            throw new BusinessException(message == null || message.isEmpty() ? "退款失败，请稍后重试" : message);
        }

        // 本地终态（6→3 + 订单编排）在一个事务内完成
        boolean finalized = refundExecutor.finalizeRefund(afterSale.getId());
        if (finalized) {
            // 库存回补放在终态事务提交之后：避免"远程回补成功 + 本地回滚"被重试放大成重复回补。
            // （restoreStock 是 stock = stock + q，不幂等，因此没有纳入补偿任务表）
            refundExecutor.restoreStockIfUnshipped(afterSale);
            // 结算冲减的任务已在 finalizeRefund 的事务内登记，这里只做"提交后立刻驱动一次"，
            // 失败由 CompensationTaskScheduler 按退避重试，不会像以前那样只剩一行 error 日志。
            compensationService.executeNow(CompensationType.SETTLE_ADJUST, afterSale.getAfterSaleNo());
        }
    }

    @Override
    public List<Long> listStuckRefundIds(int olderThanMinutes, int limit) {
        // 对账兜底：远程退款成功后若终态回写失败（进程崩溃、订单编排抛错），售后单会停在「退款中」，
        // 由定时任务重新驱动（退款按 afterSaleNo 幂等，重复驱动安全）
        LocalDateTime deadline = LocalDateTime.now().minusMinutes(olderThanMinutes);
        return afterSaleMapper.selectList(new LambdaQueryWrapper<AfterSale>()
                        .eq(AfterSale::getStatus, 6)
                        .le(AfterSale::getUpdatedAt, deadline)
                        .orderByAsc(AfterSale::getId)
                        .last("LIMIT " + Math.max(limit, 1)))
                .stream()
                .map(AfterSale::getId)
                .collect(Collectors.toList());
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

    /**
     * 该明细当前最多可退金额 = min(明细实付, 订单剩余可退)。
     *
     * <p>明细实付不能直接取 {@code t_order_item.subtotal}：那是 {@code price * quantity} 的税前小计，
     * 不含优惠券分摊（建单时按占比把 discountAmount 摊到各子订单）。用券订单按 subtotal 退，
     * 首笔就能把同单其他商品享受的优惠一并退走 —— byw-pay 的"累计退款 ≤ 实付"只能兜住总额，
     * 兜不住这种把别人的额度退给自己的串额。
     *
     * <p>再扣掉本单已完成的退款金额，避免多笔售后叠加超出实付。分摊各自四舍五入可能差一分，
     * 由这个剩余可退上限兜住。
     */
    private BigDecimal calcMaxRefundable(Order order, OrderItem item) {
        BigDecimal subtotal = item.getSubtotal() == null ? BigDecimal.ZERO : item.getSubtotal();
        BigDecimal orderTotal = order.getTotalAmount();
        BigDecimal discount = order.getDiscountAmount();
        BigDecimal itemPaid = subtotal;
        if (discount != null && discount.signum() > 0
                && orderTotal != null && orderTotal.signum() > 0) {
            BigDecimal discountShare = discount.multiply(subtotal)
                    .divide(orderTotal, 2, RoundingMode.HALF_UP);
            itemPaid = subtotal.subtract(discountShare).max(BigDecimal.ZERO);
        }
        BigDecimal payAmount = order.getPayAmount() == null ? BigDecimal.ZERO : order.getPayAmount();
        BigDecimal refunded = afterSaleMapper.selectList(new LambdaQueryWrapper<AfterSale>()
                        .eq(AfterSale::getOrderNo, order.getOrderNo())
                        .in(AfterSale::getType, REFUND_TYPES)
                        .eq(AfterSale::getStatus, 3))
                .stream()
                .map(AfterSale::getRefundAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal remaining = payAmount.subtract(refunded).max(BigDecimal.ZERO);
        return itemPaid.min(remaining);
    }

    /** 加载售后单并校验商家本店归属（平台管理员 shopId 为空则跳过校验） */
    private AfterSale requireShopAfterSale(Long id) {
        AfterSale afterSale = afterSaleMapper.selectById(id);
        if (afterSale == null) {
            throw new BusinessException("售后单不存在");
        }
        // fail-closed：无店铺上下文且非平台管理员一律拒绝（原实现"shopId 为空则跳过"可被游客绕过）
        if (!UserContext.canAccessShop(afterSale.getShopId())) {
            throw new BusinessException("无权操作该售后单");
        }
        return afterSale;
    }

    /** 生成售后单号：AS + 雪花ID（全局唯一，多实例安全） */
    private String generateAfterSaleNo() {
        return "AS" + SnowflakeIdGenerator.nextIdStr();
    }
}
