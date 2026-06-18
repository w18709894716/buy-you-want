package com.byw.pay.strategy;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * 微信支付策略
 */
@Slf4j
@Component("wechat")
public class WechatPayStrategy implements PayStrategy {

    @Override
    public Map<String, String> pay(String payNo, BigDecimal amount) {
        log.info("微信支付: payNo={}, amount={}", payNo, amount);
        Map<String, String> result = new HashMap<>();
        result.put("payUrl", "weixin://wxpay/bizpayurl?pr=" + payNo + "&amount=" + amount);
        result.put("channel", "wechat");
        result.put("tradeNo", "WX" + System.currentTimeMillis());
        return result;
    }

    @Override
    public Integer queryStatus(String payNo) {
        log.info("查询微信支付状态: payNo={}", payNo);
        // 模拟支付成功
        return 1;
    }
}
