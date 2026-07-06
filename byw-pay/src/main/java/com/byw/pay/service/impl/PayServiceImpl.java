package com.byw.pay.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.byw.api.pay.dto.PayOrderDTO;
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
        // 查找该订单的支付单
        PayOrder payOrder = payOrderMapper.selectOne(
                new LambdaQueryWrapper<PayOrder>()
                        .eq(PayOrder::getOrderNo, orderNo)
                        .eq(PayOrder::getStatus, 1));

        if (payOrder == null) {
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

        log.info("退款成功: refundNo={}, orderNo={}, amount={}", refundNo, orderNo, amount);
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
