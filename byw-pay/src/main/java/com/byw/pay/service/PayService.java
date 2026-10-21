package com.byw.pay.service;

import com.byw.api.pay.dto.PayOrderDTO;
import com.byw.api.pay.dto.RefundInfoDTO;

import java.math.BigDecimal;

public interface PayService {

    /**
     * 创建支付单。
     *
     * <p>支付金额以订单 payAmount 为准，入参 amount 仅用于核对（不一致直接拒绝），
     * 同时校验订单归属与待支付状态；同一订单已有待支付支付单时复用。
     */
    PayOrderDTO createPayOrder(String orderNo, Long userId, BigDecimal amount, String channel);

    /**
     * 获取支付状态
     */
    Integer getPayStatus(String payNo);

    /**
     * 获取支付单归属用户ID（供控制层做归属校验）
     */
    Long getPayOrderUserId(String payNo);

    /**
     * 处理真实渠道的支付回调（验签已在控制层完成）。
     */
    void handlePayCallback(String channel, String payNo, String tradeNo, String callbackContent);

    /**
     * 处理模拟支付回调（本地/演示用）：仅 {@code byw.pay.mock-enabled=true} 时可用，
     * 且必须由支付单归属用户触发。
     */
    void handleMockCallback(String channel, String payNo, String tradeNo);

    /**
     * 支付成功回调的本地事务：将支付单置为已支付。
     * 由事务消息的半消息落盘后调用，必须经 Spring 代理调用才会开启事务。
     */
    void completePayment(String payNo, String tradeNo, String callbackContent);

    /**
     * 退款。
     *
     * @param refNo 业务幂等键（售后单号等），同一 refNo 重复调用只退一次；手工退款可传 null
     */
    void refund(String orderNo, BigDecimal amount, String reason, String refNo);

    /**
     * 查询某订单的退款明细（最新一条退款记录，含原支付渠道），无则返回 null
     */
    RefundInfoDTO getRefundByOrderNo(String orderNo);
}
