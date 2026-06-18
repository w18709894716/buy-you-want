package com.byw.pay.strategy;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * 支付宝支付策略
 */
@Slf4j
@Component("alipay")
public class AlipayStrategy implements PayStrategy {

    @Override
    public Map<String, String> pay(String payNo, BigDecimal amount) {
        log.info("支付宝支付: payNo={}, amount={}", payNo, amount);
        Map<String, String> result = new HashMap<>();
        result.put("payUrl", "https://openapi.alipay.com/gateway.do?out_trade_no=" + payNo + "&total_amount=" + amount);
        result.put("channel", "alipay");
        result.put("tradeNo", "ALI" + System.currentTimeMillis());
        return result;
    }

    @Override
    public Integer queryStatus(String payNo) {
        log.info("查询支付宝支付状态: payNo={}", payNo);
        // 模拟支付成功
        return 1;
    }
}
