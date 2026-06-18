package com.byw.pay.strategy;

import com.byw.common.core.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 支付策略工厂
 */
@Component
@RequiredArgsConstructor
public class PayStrategyFactory {

    private final Map<String, PayStrategy> strategyMap;

    /**
     * 根据支付渠道获取对应的支付策略
     * @param channel 支付渠道 (alipay / wechat)
     */
    public PayStrategy getStrategy(String channel) {
        PayStrategy strategy = strategyMap.get(channel);
        if (strategy == null) {
            throw new BusinessException("不支持的支付渠道: " + channel);
        }
        return strategy;
    }
}
