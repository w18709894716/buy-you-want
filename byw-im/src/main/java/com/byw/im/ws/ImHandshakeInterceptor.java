package com.byw.im.ws;

import com.byw.common.core.constant.CommonConstants;
import com.byw.common.redis.util.RedisUtil;
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
    private final RedisUtil redisUtil;

    /** 客服工作台权限码（与商家端菜单 perm_code / 前端路由 meta.perm 一致） */
    private static final String IM_WORKBENCH_PERM = "m:im:workbench";

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
        attributes.put(SessionManager.ATTR_IS_MERCHANT, merchant);
        if (shopId != null) {
            attributes.put(SessionManager.ATTR_SHOP_ID, shopId);
        }
        attributes.put(SessionManager.ATTR_PRINCIPAL, principal);
        // 商家角色记录客服姓名（JWT 中的 username），用于消息 senderName
        if (merchant) {
            String staffName = jwtUtil.getUsername(token);
            attributes.put(SessionManager.ATTR_STAFF_NAME, staffName);
            // 校验客服接待权限：无权限的商家账号仅维持连接，不参与自动分配
            attributes.put(SessionManager.ATTR_CAN_SERVE, canServeIm(userId));
        }
        log.info("IM 握手通过：userId={}, role={}, shopId={}, principal={}", userId, role, shopId, principal);
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // no-op
    }

    /**
     * 判断商家账号是否具备客服接待权限：主账号（"*"）或拥有 m:im:workbench 权限码。
     * 权限集缺失（如老登录态）或校验异常时默认放行，仅明确无权限时排除。
     */
    private boolean canServeIm(Long userId) {
        try {
            String key = CommonConstants.AUTH_PERMS_KEY_PREFIX + CommonConstants.USER_TYPE_MERCHANT + ":" + userId;
            if (!Boolean.TRUE.equals(redisUtil.hasKey(key))) {
                log.warn("IM 商家账号权限集缺失，默认放行：userId={}", userId);
                return true;
            }
            return Boolean.TRUE.equals(redisUtil.sIsMember(key, CommonConstants.PERM_ALL))
                    || Boolean.TRUE.equals(redisUtil.sIsMember(key, IM_WORKBENCH_PERM));
        } catch (Exception e) {
            log.warn("IM 权限校验异常，默认放行：userId={}, err={}", userId, e.getMessage());
            return true;
        }
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
