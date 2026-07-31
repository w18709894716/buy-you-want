package com.byw.im.ws;

import com.byw.common.core.constant.CommonConstants;
import com.byw.common.security.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.Map;

/**
 * WebSocket 握手鉴权：浏览器 WebSocket 无法自定义请求头，
 * 故前端以 {@code ws://gateway/ws/im?token=<JWT>} 方式传入 token，
 * 此处解析并校验，将身份写入 session 属性供 Handler 使用。非法 token 拒绝握手。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ImHandshakeInterceptor implements HandshakeInterceptor {

    private final JwtUtil jwtUtil;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        String token = resolveToken(request.getURI());
        if (token == null || !jwtUtil.validateToken(token)) {
            log.warn("IM 握手被拒绝：token 缺失或非法");
            return false;
        }
        Long userId = jwtUtil.getUserId(token);
        String role = jwtUtil.getRole(token);
        Long shopId = jwtUtil.getShopId(token);
        if (userId == null) {
            log.warn("IM 握手被拒绝：token 无 userId");
            return false;
        }
        if (role == null) {
            role = CommonConstants.ROLE_USER;
        }

        boolean merchant = CommonConstants.ROLE_MERCHANT_OWNER.equals(role)
                || CommonConstants.ROLE_MERCHANT_STAFF.equals(role);
        if (merchant && shopId == null) {
            log.warn("IM 握手被拒绝：商家账号缺少 shopId, userId={}", userId);
            return false;
        }

        String principal = merchant ? SessionManager.shopPrincipal(shopId) : SessionManager.userPrincipal(userId);
        attributes.put(SessionManager.ATTR_USER_ID, userId);
        attributes.put(SessionManager.ATTR_ROLE, role);
        if (shopId != null) {
            attributes.put(SessionManager.ATTR_SHOP_ID, shopId);
        }
        attributes.put(SessionManager.ATTR_PRINCIPAL, principal);
        log.info("IM 握手通过：userId={}, role={}, shopId={}, principal={}", userId, role, shopId, principal);
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // no-op
    }

    private String resolveToken(URI uri) {
        Map<String, List<String>> params = UriComponentsBuilder.fromUri(uri).build().getQueryParams();
        List<String> values = params.get("token");
        if (values == null || values.isEmpty()) {
            return null;
        }
        String token = values.get(0);
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        return token;
    }
}
