package com.byw.pay.strategy;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 支付策略接口
 */
public interface PayStrategy {

    /**
     * 发起支付
     * @param payNo 支付单号
     * @param amount 支付金额
     * @return 支付结果信息（包含payUrl等）
     */
    Map<String, String> pay(String payNo, BigDecimal amount);

    /**
     * 查询支付状态
     * @param payNo 支付单号
     * @return 支付状态: 0待支付 1支付成功 2支付失败
     */
    Integer queryStatus(String payNo);
}
