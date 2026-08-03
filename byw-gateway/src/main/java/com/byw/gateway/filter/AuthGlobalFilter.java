package com.byw.gateway.filter;

import com.byw.common.core.constant.CommonConstants;
import com.byw.common.security.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 网关软认证过滤器：
 * - 对所有请求无条件剥离客户端自带的身份头（防伪造）；
 * - 携带合法 Bearer token 时解析并注入身份头，无/失效 token 以游客身份放行；
 * - 任何情况都不在网关 401，是否需要登录由各服务的 @Public / 默认关闭策略在服务层判定。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthGlobalFilter implements GlobalFilter, Ordered {

    private final JwtUtil jwtUtil;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();

        // 无条件剥离客户端伪造的身份头
        ServerHttpRequest.Builder builder = request.mutate()
                .headers(headers -> {
                    headers.remove(CommonConstants.HEADER_USER_ID);
                    headers.remove(CommonConstants.HEADER_USERNAME);
                    headers.remove(CommonConstants.HEADER_USER_ROLE);
                    headers.remove(CommonConstants.HEADER_SHOP_ID);
                    headers.remove(CommonConstants.HEADER_USER_TYPE);
                });

        // 有合法 token 才注入身份头；无/失效 token 以游客身份放行
        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            if (jwtUtil.validateToken(token)) {
                Long userId = jwtUtil.getUserId(token);
                String username = jwtUtil.getUsername(token);
                String role = jwtUtil.getRole(token);
                Long shopId = jwtUtil.getShopId(token);
                String userType = jwtUtil.getUserType(token);

                builder.header(CommonConstants.HEADER_USER_ID, String.valueOf(userId))
                        .header(CommonConstants.HEADER_USERNAME, username);
                if (role != null) {
                    builder.header(CommonConstants.HEADER_USER_ROLE, role);
                }
                if (shopId != null) {
                    builder.header(CommonConstants.HEADER_SHOP_ID, String.valueOf(shopId));
                }
                if (userType != null) {
                    builder.header(CommonConstants.HEADER_USER_TYPE, userType);
                }
            }
        }

        return chain.filter(exchange.mutate().request(builder.build()).build());
    }

    @Override
    public int getOrder() {
        return -100;
    }
}
