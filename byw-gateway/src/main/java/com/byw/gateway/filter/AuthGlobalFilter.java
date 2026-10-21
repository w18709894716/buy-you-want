package com.byw.gateway.filter;

import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpUtil;
import com.byw.common.core.constant.CommonConstants;
import com.byw.common.core.util.LoginIds;
import com.byw.common.security.sign.InternalSignature;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import reactor.core.publisher.Mono;

/**
 * 网关软认证过滤器：
 * - 对所有请求无条件剥离客户端自带的身份头（防伪造）；
 * - 携带合法 Bearer token 时解析并注入身份头（附 HMAC 签名，服务端据此认定来源），无/失效 token 以游客身份放行；
 * - 任何情况都不在网关 401，是否需要登录由各服务的 @Public / 默认关闭策略在服务层判定。
 */
@Slf4j
@Component
public class AuthGlobalFilter implements GlobalFilter, Ordered {

    private final String internalSecret;

    public AuthGlobalFilter(
            @Value("${" + InternalSignature.PROPERTY_NAME + ":" + InternalSignature.DEFAULT_SECRET + "}")
            String internalSecret) {
        this.internalSecret = internalSecret;
        InternalSignature.warnIfDefaultSecret(internalSecret, log);
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();

        // 无条件剥离客户端伪造的身份头（含签名头，签名由本过滤器重新生成）
        ServerHttpRequest.Builder builder = request.mutate()
                .headers(headers -> {
                    headers.remove(CommonConstants.HEADER_USER_ID);
                    headers.remove(CommonConstants.HEADER_USERNAME);
                    headers.remove(CommonConstants.HEADER_USER_ROLE);
                    headers.remove(CommonConstants.HEADER_SHOP_ID);
                    headers.remove(CommonConstants.HEADER_USER_TYPE);
                    headers.remove(CommonConstants.HEADER_IDENTITY_SIGNATURE);
                    // 服务间凭证同样只允许内部 Feign 生成，客户端传入一律剥离
                    headers.remove(CommonConstants.HEADER_INTERNAL_SIGNATURE);
                });

        // 有合法 token 才注入身份头；无/失效 token 以游客身份放行
        // 双模式：Authorization 头优先，其次 HttpOnly Cookie（前端可只用 Cookie，不再把 token 暴露给 JS）
        List<String> candidates = resolveTokens(request);
        if (!candidates.isEmpty()) {
            try {
                // 按顺序取第一个能校验通过的：不能用"拿到就用"，否则浏览器里的失效 Cookie
                // 会把有效的 Authorization 头顶掉，表现为"登录成功后又立刻退出"。
                Object loginId = null;
                SaSession session = null;
                for (String candidate : candidates) {
                    Object id = StpUtil.getLoginIdByToken(candidate);
                    if (id == null) {
                        continue;
                    }
                    SaSession candidateSession = StpUtil.getSessionByLoginId(id);
                    if (candidateSession == null) {
                        continue;
                    }
                    loginId = id;
                    session = candidateSession;
                    break;
                }
                // loginId 形如 "c:1"/"sys:1"/"merchant:1"（三端 ID 空间隔离），兼容历史纯数字 loginId
                Long userIdValue = LoginIds.userId(loginId);
                if (session != null && userIdValue != null) {
                    String userId = String.valueOf(userIdValue);
                    String username = session.getString(CommonConstants.SESSION_USERNAME);
                    String role = session.getString(CommonConstants.SESSION_ROLE);
                    // getLong 缺省返回 0；0/负数都表示"没有店铺上下文"，不能再注入 X-Shop-Id: 0，
                    // 否则平台管理员对"平台级资源"（shop_id 为 null/0 的券、Banner 等）会被店铺校验拒绝
                    Object rawShopId = session.get(CommonConstants.SESSION_SHOP_ID);
                    Long shopId = rawShopId instanceof Number number ? number.longValue() : null;
                    String userType = session.getString(CommonConstants.SESSION_USER_TYPE);
                    String shopIdValue = shopId == null || shopId <= 0 ? null : String.valueOf(shopId);

                    builder.header(CommonConstants.HEADER_USER_ID, userId);
                    if (username != null) {
                        // 头值按 ISO-8859-1 传输，中文用户名必须编码后再写；签名（下方）仍用原文
                        builder.header(CommonConstants.HEADER_USERNAME, InternalSignature.encodeUsername(username));
                    }
                    if (role != null) {
                        builder.header(CommonConstants.HEADER_USER_ROLE, role);
                    }
                    if (shopIdValue != null) {
                        builder.header(CommonConstants.HEADER_SHOP_ID, shopIdValue);
                    }
                    if (userType != null) {
                        builder.header(CommonConstants.HEADER_USER_TYPE, userType);
                    }
                    builder.header(CommonConstants.HEADER_IDENTITY_SIGNATURE,
                            InternalSignature.sign(internalSecret, userId, username, role, shopIdValue, userType));
                }
            } catch (Exception e) {
                // token 被顶下线/踢下线/会话异常等，按游客放行（软认证语义）
                log.warn("网关 token 校验失败，按游客放行：err={}", e.getMessage());
            }
        }

        return chain.filter(exchange.mutate().request(builder.build()).build());
    }

    /**
     * 双模式取 token：Authorization: Bearer 优先，其次 HttpOnly Cookie。
     *
     * <p><b>三端 Cookie 必须分开取</b>：三个前端通常同 host（本地都是 localhost，仅端口不同），
     * 而 Cookie 不区分端口。若统一用一个 Cookie 名，浏览器里后登录的端会覆盖前面的，
     * 管理端就会拿着 C 端/商家端的身份去调接口 → 403。
     * 选择顺序：先按 X-Client-App 头（缺省用路径前缀）定位本端 Cookie，找到就用它；
     * 找不到才退回 Authorization 头 —— Cookie 优先是为了不被浏览器里残留的旧头污染。
     */
    /**
     * 按优先级返回候选 token（去重）：同端 Cookie → Authorization 头。
     * 调用方必须**逐个校验**并取第一个有效的 —— 盲信第一个会把失效 Cookie 当成未登录。
     */
    private List<String> resolveTokens(ServerHttpRequest request) {
        List<String> tokens = new ArrayList<>(3);
        Map<String, String> cookies = parseCookies(request.getHeaders().getFirst(HttpHeaders.COOKIE));
        String path = request.getURI().getPath();
        String clientApp = request.getHeaders().getFirst(CommonConstants.HEADER_CLIENT_APP);

        if (path != null && path.startsWith("/ws/im")) {
            // IM WebSocket：商家工作台与 C 端聊天共用路径，无法靠路径区分，按端逐个尝试
            addToken(tokens, cookies.get(CommonConstants.AUTH_COOKIE_MERCHANT));
            addToken(tokens, cookies.get(CommonConstants.AUTH_COOKIE_NAME));
            addToken(tokens, cookies.get(CommonConstants.AUTH_COOKIE_ADMIN));
        } else {
            addToken(tokens, cookies.get(cookieNameFor(clientApp, path)));
        }
        // 兜底：Authorization: Bearer（Nuxt 等仅用头的前端，以及 Cookie 失效时的有效头）
        addToken(tokens, bearerToken(request));
        return tokens;
    }

    private void addToken(List<String> tokens, String token) {
        if (token != null && !token.isBlank() && !tokens.contains(token)) {
            tokens.add(token);
        }
    }
    private String bearerToken(ServerHttpRequest request) {
        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String value = authHeader.substring(7).trim();
            return value.isEmpty() ? null : value;
        }
        return null;
    }

    /** 按端选 Cookie 名：X-Client-App 优先，其次按路径前缀，最后 C 端 */
    private String cookieNameFor(String clientApp, String path) {
        if (clientApp != null && !clientApp.isBlank()) {
            String app = clientApp.trim().toLowerCase();
            if (CommonConstants.CLIENT_APP_ADMIN.equals(app)) {
                return CommonConstants.AUTH_COOKIE_ADMIN;
            }
            if (CommonConstants.CLIENT_APP_MERCHANT.equals(app)) {
                return CommonConstants.AUTH_COOKIE_MERCHANT;
            }
            return CommonConstants.AUTH_COOKIE_NAME;
        }
        if (path != null && path.startsWith("/api/admin")) {
            return CommonConstants.AUTH_COOKIE_ADMIN;
        }
        if (path != null && path.startsWith("/api/merchant")) {
            return CommonConstants.AUTH_COOKIE_MERCHANT;
        }
        return CommonConstants.AUTH_COOKIE_NAME;
    }

    private Map<String, String> parseCookies(String cookieHeader) {
        Map<String, String> cookies = new HashMap<>();
        if (cookieHeader == null || cookieHeader.isEmpty()) {
            return cookies;
        }
        for (String part : cookieHeader.split(";")) {
            String item = part.trim();
            int idx = item.indexOf('=');
            if (idx > 0) {
                cookies.put(item.substring(0, idx), item.substring(idx + 1).trim());
            }
        }
        return cookies;
    }

    @Override
    public int getOrder() {
        return -100;
    }
}
