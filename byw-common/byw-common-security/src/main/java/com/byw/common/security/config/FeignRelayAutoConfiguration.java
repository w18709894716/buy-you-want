package com.byw.common.security.config;

import com.byw.common.security.feign.FeignAuthRelayInterceptor;
import feign.RequestInterceptor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * 仅在存在 Feign 时生效：注册身份透传拦截器。
 */
@AutoConfiguration
@ConditionalOnClass(RequestInterceptor.class)
public class FeignRelayAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public FeignAuthRelayInterceptor feignAuthRelayInterceptor() {
        return new FeignAuthRelayInterceptor();
    }
}
