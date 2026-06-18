package com.byw.common.security.config;

import com.byw.common.security.interceptor.AuthInterceptor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.HandlerInterceptor;

@AutoConfiguration(after = JwtAutoConfiguration.class)
@ConditionalOnClass(HandlerInterceptor.class)
public class SecurityMvcAutoConfiguration {

    @Bean
    public AuthInterceptor authInterceptor() {
        return new AuthInterceptor();
    }

    @Bean
    public WebMvcConfig webMvcConfig(AuthInterceptor authInterceptor) {
        return new WebMvcConfig(authInterceptor);
    }
}
