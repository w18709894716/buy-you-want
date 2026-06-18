package com.byw.pay.service;

import com.byw.api.pay.dto.PayOrderDTO;

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
     * 处理支付回调
     */
    void handlePayCallback(String channel, String payNo, String tradeNo, String callbackContent);

    /**
     * 退款
     */
    void refund(String orderNo, BigDecimal amount, String reason);
}
