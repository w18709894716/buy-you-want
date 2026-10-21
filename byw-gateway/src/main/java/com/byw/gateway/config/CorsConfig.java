package com.byw.gateway.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 网关跨域配置。
 *
 * <p>原实现是 {@code addAllowedOriginPattern("*")} + {@code setAllowCredentials(true)} ——
 * 这等于对全网开放跨域并允许携带凭证，任何站点都能带着用户 Cookie/凭据调用本 API。
 * 现改为**来源白名单**（可通过 {@code byw.security.cors.allowed-origins} 或
 * 环境变量 {@code BYW_CORS_ALLOWED_ORIGINS} 覆盖）；若显式配置为 {@code *}，
 * 则强制关闭 {@code allowCredentials}（通配 + 凭证本就会被浏览器拒绝）。
 */
@Slf4j
@Configuration
public class CorsConfig {

    /** 暴露 traceId，便于前端把报错与后端日志对上 */
    private static final String TRACE_ID_HEADER = "X-Trace-Id";

    @Value("${byw.security.cors.allowed-origins:http://localhost:3000,http://127.0.0.1:3000,"
            + "http://localhost:5174,http://127.0.0.1:5174,http://localhost:5175,http://127.0.0.1:5175}")
    private String allowedOrigins;

    /**
     * 来源模式（支持通配），用于「用 IP / 局域网地址访问也要能用」的场景：
     * 浏览器把页面 host 原样放进 Origin 头，写死 localhost 的名单会让 192.168.x.x 访问直接 403。
     * 只有确实需要放开时才配；生产应使用明确的域名（allowed-origins）。
     */
    @Value("${byw.security.cors.allowed-origin-patterns:http://localhost:*,http://127.0.0.1:*}")
    private String allowedOriginPatterns;

    @Bean
    public CorsWebFilter corsWebFilter() {
        List<String> origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toList();

        List<String> patterns = Arrays.stream(allowedOriginPatterns.split(","))
                .map(String::trim)
                .filter(p -> !p.isEmpty())
                .toList();

        CorsConfiguration config = new CorsConfiguration();
        if (origins.contains("*")) {
            config.addAllowedOriginPattern("*");
            config.setAllowCredentials(false);
            log.warn("CORS 配置为通配 '*'：已强制关闭 allowCredentials；"
                    + "生产环境请用 byw.security.cors.allowed-origins 指定前端域名");
        } else {
            // 显式域名 + 模式（模式支持通配且**保留 credentials**，因此不再需要用 "*"）
            List<String> merged = new ArrayList<>(origins);
            merged.addAll(patterns);
            config.setAllowedOriginPatterns(merged);
            config.setAllowCredentials(true);
            log.info("CORS 允许来源: {}；模式: {}", origins, patterns);
        }
        config.addAllowedHeader("*");
        config.addAllowedMethod("*");
        config.setMaxAge(3600L);
        config.setExposedHeaders(List.of(TRACE_ID_HEADER));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return new CorsWebFilter(source);
    }
}
