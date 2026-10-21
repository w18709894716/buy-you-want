package com.byw.im.config;

import com.byw.im.ws.ImHandshakeInterceptor;
import com.byw.im.ws.ImWebSocketHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import java.util.Arrays;

/**
 * 注册原生 WebSocket 端点 /ws/im，挂载握手鉴权拦截器与消息处理器。
 * 网关以 lb:ws://byw-im 转发 /ws/im/** 到此端点。
 *
 * <p>跨域来源原为 {@code "*"}：WebSocket 握手不受同源策略保护，
 * 任意站点都能携带用户浏览器里的凭据发起连接（CSWSH）。
 * 现改为**来源白名单**（{@code byw.security.ws.allowed-origins}）。
 */
@Slf4j
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final ImWebSocketHandler imWebSocketHandler;
    private final ImHandshakeInterceptor imHandshakeInterceptor;

    @Value("${byw.security.ws.allowed-origins:http://localhost:3000,http://127.0.0.1:3000,"
            + "http://localhost:5174,http://127.0.0.1:5174,http://localhost:5175,http://127.0.0.1:5175}")
    private String allowedOrigins;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        String[] origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toArray(String[]::new);
        if (origins.length == 1 && "*".equals(origins[0])) {
            log.warn("WebSocket 允许任意来源(*)：存在跨站 WebSocket 劫持风险，"
                    + "生产环境请用 byw.security.ws.allowed-origins 指定前端域名");
        }
        registry.addHandler(imWebSocketHandler, "/ws/im")
                .addInterceptors(imHandshakeInterceptor)
                .setAllowedOriginPatterns(origins);
    }
}
