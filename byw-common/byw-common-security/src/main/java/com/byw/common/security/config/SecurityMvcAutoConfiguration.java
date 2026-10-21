package com.byw.common.security.config;

import com.byw.common.redis.util.RedisUtil;
import com.byw.common.security.interceptor.AuthInterceptor;
import com.byw.common.security.service.PermissionChecker;
import com.byw.common.security.sign.InternalSignature;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.HandlerInterceptor;

@AutoConfiguration
@ConditionalOnClass(HandlerInterceptor.class)
public class SecurityMvcAutoConfiguration {

    @Bean
    @ConditionalOnBean(RedisUtil.class)
    public PermissionChecker permissionChecker(RedisUtil redisUtil) {
        return new PermissionChecker(redisUtil);
    }

    @Bean
    public AuthInterceptor authInterceptor(ObjectProvider<PermissionChecker> permissionChecker,
            @Value("${" + InternalSignature.PROPERTY_NAME + ":}")
            String internalSecret) {
        // 无兜底默认值：密钥缺失或仍是源码默认值时直接启动失败（见 requireUsableSecret）
        InternalSignature.requireUsableSecret(internalSecret);
        return new AuthInterceptor(permissionChecker.getIfAvailable(), internalSecret);
    }

    @Bean
    public WebMvcConfig webMvcConfig(AuthInterceptor authInterceptor) {
        return new WebMvcConfig(authInterceptor);
    }
}
