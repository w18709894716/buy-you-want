package com.byw.common.security.config;

import com.byw.common.security.util.JwtUtil;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
public class JwtAutoConfiguration {

    @Bean
    public JwtUtil jwtUtil() {
        return new JwtUtil();
    }
}
