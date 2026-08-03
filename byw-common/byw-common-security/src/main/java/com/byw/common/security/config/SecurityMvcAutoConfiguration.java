package com.byw.common.security.config;

import com.byw.common.redis.util.RedisUtil;
import com.byw.common.security.interceptor.AuthInterceptor;
import com.byw.common.security.service.PermissionChecker;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.HandlerInterceptor;

@AutoConfiguration(after = JwtAutoConfiguration.class)
@ConditionalOnClass(HandlerInterceptor.class)
public class SecurityMvcAutoConfiguration {

    @Bean
    @ConditionalOnBean(RedisUtil.class)
    public PermissionChecker permissionChecker(RedisUtil redisUtil) {
        return new PermissionChecker(redisUtil);
    }

    @Bean
    public AuthInterceptor authInterceptor(ObjectProvider<PermissionChecker> permissionChecker) {
        return new AuthInterceptor(permissionChecker.getIfAvailable());
    }

    @Bean
    public WebMvcConfig webMvcConfig(AuthInterceptor authInterceptor) {
        return new WebMvcConfig(authInterceptor);
    }
}
