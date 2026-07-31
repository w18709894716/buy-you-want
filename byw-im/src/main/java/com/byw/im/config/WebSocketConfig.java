package com.byw.im.config;

import com.byw.im.ws.ImHandshakeInterceptor;
import com.byw.im.ws.ImWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * 注册原生 WebSocket 端点 /ws/im，挂载握手鉴权拦截器与消息处理器。
 * 网关以 lb:ws://byw-im 转发 /ws/im/** 到此端点。
 */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final ImWebSocketHandler imWebSocketHandler;
    private final ImHandshakeInterceptor imHandshakeInterceptor;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(imWebSocketHandler, "/ws/im")
                .addInterceptors(imHandshakeInterceptor)
                .setAllowedOriginPatterns("*");
    }
}
