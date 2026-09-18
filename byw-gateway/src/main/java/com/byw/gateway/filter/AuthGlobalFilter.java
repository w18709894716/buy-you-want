package com.byw.gateway.filter;

import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpUtil;
import com.byw.common.core.constant.CommonConstants;
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
public class AuthGlobalFilter implements GlobalFilter, Ordered {

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
            try {
                Object loginId = StpUtil.getLoginIdByToken(token);
                SaSession session = loginId == null ? null : StpUtil.getSessionByLoginId(loginId);
                if (session != null) {
                    String username = session.getString(CommonConstants.SESSION_USERNAME);
                    String role = session.getString(CommonConstants.SESSION_ROLE);
                    Long shopId = session.getLong(CommonConstants.SESSION_SHOP_ID);
                    String userType = session.getString(CommonConstants.SESSION_USER_TYPE);

                    builder.header(CommonConstants.HEADER_USER_ID, loginId.toString())
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
            } catch (Exception e) {
                // token 被顶下线/踢下线/会话异常等，按游客放行（软认证语义）
                log.warn("网关 token 校验失败，按游客放行：err={}", e.getMessage());
            }
        }

        return chain.filter(exchange.mutate().request(builder.build()).build());
    }

    @Override
    public int getOrder() {
        return -100;
    }
}
