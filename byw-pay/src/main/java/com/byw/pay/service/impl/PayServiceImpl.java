package com.byw.pay.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.byw.api.order.OrderFeignClient;
import com.byw.api.order.dto.OrderDetailDTO;
import com.byw.api.pay.dto.PayOrderDTO;
import com.byw.common.core.result.R;
import com.byw.api.pay.dto.RefundInfoDTO;
import com.byw.common.core.exception.BusinessException;
import com.byw.pay.entity.PayOrder;
import com.byw.pay.entity.RefundRecord;
import com.byw.pay.mapper.PayOrderMapper;
import com.byw.pay.mapper.RefundRecordMapper;
import com.byw.pay.producer.PayEventProducer;
import com.byw.pay.service.PayService;
import com.byw.pay.strategy.PayStrategy;
import com.byw.pay.strategy.PayStrategyFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
@RequiredArgsConstructor
public class PayServiceImpl implements PayService {

    private final PayOrderMapper payOrderMapper;
    private final RefundRecordMapper refundRecordMapper;
    private final PayStrategyFactory payStrategyFactory;
    private final PayEventProducer payEventProducer;
    private final OrderFeignClient orderFeignClient;

    private static final AtomicLong SEQUENCE = new AtomicLong(0);

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PayOrderDTO createPayOrder(String orderNo, Long userId, BigDecimal amount, String channel) {
        // 生成支付单号
        String payNo = generatePayNo();

        // 使用策略模式发起支付
        PayStrategy strategy = payStrategyFactory.getStrategy(channel);
        Map<String, String> payResult = strategy.pay(payNo, amount);

        // 创建支付单
        PayOrder payOrder = new PayOrder();
        payOrder.setPayNo(payNo);
        payOrder.setOrderNo(orderNo);
        payOrder.setUserId(userId);
        payOrder.setAmount(amount);
        payOrder.setPayChannel(channel);
        payOrder.setStatus(0); // 待支付
        payOrderMapper.insert(payOrder);

        // 构建返回DTO
        PayOrderDTO dto = new PayOrderDTO();
        BeanUtils.copyProperties(payOrder, dto);
        dto.setPayUrl(payResult.get("payUrl"));

        log.info("创建支付单成功: payNo={}, orderNo={}, channel={}", payNo, orderNo, channel);
        return dto;
    }

    @Override
    public Integer getPayStatus(String payNo) {
        PayOrder payOrder = getPayOrderByNo(payNo);
        return payOrder.getStatus();
    }

    @Override
    public Long getPayOrderUserId(String payNo) {
        return getPayOrderByNo(payNo).getUserId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handlePayCallback(String channel, String payNo, String tradeNo, String callbackContent) {
        PayOrder payOrder = getPayOrderByNo(payNo);

        if (payOrder.getStatus() != 0) {
            log.warn("支付单状态异常，忽略回调: payNo={}, status={}", payNo, payOrder.getStatus());
            return;
        }

        // 使用策略验证支付状态
        PayStrategy strategy = payStrategyFactory.getStrategy(channel);
        Integer payStatus = strategy.queryStatus(payNo);

        // 更新支付单状态
        payOrder.setStatus(payStatus);
        payOrder.setChannelTradeNo(tradeNo);
        payOrder.setCallbackContent(callbackContent);
        if (payStatus == 1) {
            payOrder.setPayTime(LocalDateTime.now());
        }
        payOrderMapper.updateById(payOrder);

        // 支付成功时发送RocketMQ消息通知订单服务
        if (payStatus == 1) {
            payEventProducer.sendPaymentResult(payOrder.getOrderNo(), payNo, payStatus);
            log.info("支付成功，已发送RocketMQ通知: payNo={}, orderNo={}", payNo, payOrder.getOrderNo());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void refund(String orderNo, BigDecimal amount, String reason) {
        // 查找可退款支付单：先按本单查，未命中再回溯父订单（聚合支付的支付单挂在父订单号上）
        PayOrder payOrder = findRefundablePayOrder(orderNo);

        if (payOrder == null) {
            log.warn("退款失败，未找到已支付的支付单: orderNo={}", orderNo);
            throw new BusinessException("未找到已支付的订单");
        }

        // 创建退款记录
        String refundNo = generateRefundNo();
        RefundRecord refundRecord = new RefundRecord();
        refundRecord.setRefundNo(refundNo);
        refundRecord.setPayNo(payOrder.getPayNo());
        refundRecord.setOrderNo(orderNo);
        refundRecord.setUserId(payOrder.getUserId());
        refundRecord.setRefundAmount(amount);
        refundRecord.setReason(reason);
        refundRecord.setStatus(1); // 模拟退款成功
        refundRecordMapper.insert(refundRecord);

        // 更新支付单状态为已退款
        payOrder.setStatus(3);
        payOrderMapper.updateById(payOrder);

        // 订单状态流转由 byw-order 售后流程统一编排（商品级退款不能整单关闭），此处不再回写
        log.info("退款成功: refundNo={}, orderNo={}, amount={}", refundNo, orderNo, amount);
    }

    @Override
    public RefundInfoDTO getRefundByOrderNo(String orderNo) {
        RefundRecord record = refundRecordMapper.selectOne(
                new LambdaQueryWrapper<RefundRecord>()
                        .eq(RefundRecord::getOrderNo, orderNo)
                        .orderByDesc(RefundRecord::getCreatedAt)
                        .last("LIMIT 1"));
        if (record == null) {
            return null;
        }
        RefundInfoDTO dto = new RefundInfoDTO();
        dto.setRefundNo(record.getRefundNo());
        dto.setOrderNo(record.getOrderNo());
        dto.setRefundAmount(record.getRefundAmount());
        dto.setReason(record.getReason());
        dto.setStatus(record.getStatus());
        dto.setCreatedAt(record.getCreatedAt());
        dto.setUpdatedAt(record.getUpdatedAt());
        // 关联支付单获取原支付渠道（原路退回目的地）
        PayOrder payOrder = payOrderMapper.selectOne(
                new LambdaQueryWrapper<PayOrder>()
                        .eq(PayOrder::getPayNo, record.getPayNo())
                        .last("LIMIT 1"));
        if (payOrder != null) {
            dto.setPayChannel(payOrder.getPayChannel());
        }
        return dto;
    }

    // ==================== 私有方法 ====================

    private PayOrder getPayOrderByNo(String payNo) {
        PayOrder payOrder = payOrderMapper.selectOne(
                new LambdaQueryWrapper<PayOrder>().eq(PayOrder::getPayNo, payNo));
        if (payOrder == null) {
            throw new BusinessException("支付单不存在");
        }
        return payOrder;
    }

    /**
     * 查找订单对应的可退款支付单。
     * 聚合支付场景下支付单记录的是父订单号，子订单退款时需回溯父订单号查找；
     * 状态含已退款(3)：同一父单下多个子订单先后退款时，支付单已被首笔退款置 3，后续仍可命中。
     */
    private PayOrder findRefundablePayOrder(String orderNo) {
        PayOrder payOrder = selectPaidOrder(orderNo);
        if (payOrder != null) {
            return payOrder;
        }
        // 回溯父订单号（子订单本身无支付单）
        try {
            R<OrderDetailDTO> orderResult = orderFeignClient.getOrderDetail(orderNo);
            if (orderResult != null && orderResult.isSuccess() && orderResult.getData() != null) {
                String parentOrderNo = orderResult.getData().getParentOrderNo();
                if (parentOrderNo != null && !parentOrderNo.isEmpty()) {
                    return selectPaidOrder(parentOrderNo);
                }
            }
        } catch (Exception e) {
            log.warn("查询订单父单号失败: orderNo={}, error={}", orderNo, e.getMessage());
        }
        return null;
    }

    /** 按订单号查支付成功(1)/已退款(3)的支付单，取最新一条 */
    private PayOrder selectPaidOrder(String orderNo) {
        return payOrderMapper.selectOne(
                new LambdaQueryWrapper<PayOrder>()
                        .eq(PayOrder::getOrderNo, orderNo)
                        .in(PayOrder::getStatus, 1, 3)
                        .orderByDesc(PayOrder::getCreatedAt)
                        .last("LIMIT 1"));
    }

    private String generatePayNo() {
        String datePart = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        long seq = SEQUENCE.incrementAndGet() % 100000;
        return "PAY" + datePart + String.format("%05d", seq);
    }

    private String generateRefundNo() {
        String datePart = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        long seq = SEQUENCE.incrementAndGet() % 100000;
        return "REF" + datePart + String.format("%05d", seq);
    }
}
