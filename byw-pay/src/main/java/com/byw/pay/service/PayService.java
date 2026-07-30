package com.byw.pay.service;

import com.byw.api.pay.dto.PayOrderDTO;
import com.byw.api.pay.dto.RefundInfoDTO;

import java.math.BigDecimal;

public interface PayService {

    /**
     * 创建支付单
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
     * 处理支付回调
     */
    void handlePayCallback(String channel, String payNo, String tradeNo, String callbackContent);

    /**
     * 退款
     */
    void refund(String orderNo, BigDecimal amount, String reason);

    /**
     * 查询某订单的退款明细（最新一条退款记录，含原支付渠道），无则返回 null
     */
    RefundInfoDTO getRefundByOrderNo(String orderNo);
}
