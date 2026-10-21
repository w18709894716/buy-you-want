package com.byw.common.security.interceptor;

import com.byw.common.core.constant.CommonConstants;
import com.byw.common.core.exception.BusinessException;
import com.byw.common.core.exception.ResultCode;
import com.byw.common.security.annotation.InternalApi;
import com.byw.common.security.annotation.Public;
import com.byw.common.security.annotation.RequireAdmin;
import com.byw.common.security.annotation.RequirePerm;
import com.byw.common.security.annotation.RequireRole;
import com.byw.common.security.context.UserContext;
import com.byw.common.security.service.PermissionChecker;
import com.byw.common.security.sign.InternalSignature;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

@Slf4j
public class AuthInterceptor implements HandlerInterceptor {

    private final PermissionChecker permissionChecker;
    private final String internalSecret;

    public AuthInterceptor(PermissionChecker permissionChecker, String internalSecret) {
        this.permissionChecker = permissionChecker;
        this.internalSecret = internalSecret;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 必须入口先清：下面任一鉴权分支抛异常时，Spring 不会执行本拦截器自己的 afterCompletion
        // （HandlerExecutionChain 只在 preHandle 成功返回后才推进 interceptorIndex），
        // 残留的身份会被同一线程上的下一个匿名请求继承（匿名请求没有身份头，一个字段都不会被覆盖）。
        UserContext.clear();

        String userIdStr = request.getHeader(CommonConstants.HEADER_USER_ID);
        // 用户名在头里是百分号编码的（HTTP 头只支持 ISO-8859-1，中文直传会被打坏导致验签失败），
        // 解码后才是签名时用的原文，UserContext 里也存原文
        String username = InternalSignature.decodeUsername(request.getHeader(CommonConstants.HEADER_USERNAME));
        String userRole = request.getHeader(CommonConstants.HEADER_USER_ROLE);
        String shopIdStr = request.getHeader(CommonConstants.HEADER_SHOP_ID);
        String userType = request.getHeader(CommonConstants.HEADER_USER_TYPE);

        // 身份头由网关注入、Feign 透传时重新签名；直连服务端口伪造的头签名对不上，
        // 一律按游客处理（受保护接口随后 401），避免绕过网关直接拿到管理员身份。
        boolean hasIdentity = userIdStr != null || username != null || userRole != null
                || shopIdStr != null || userType != null;
        if (hasIdentity && !InternalSignature.verify(internalSecret,
                request.getHeader(CommonConstants.HEADER_IDENTITY_SIGNATURE),
                userIdStr, username, userRole, shopIdStr, userType)) {
            log.warn("身份头签名校验失败，按游客处理: uri={}", request.getRequestURI());
            userIdStr = null;
            username = null;
            userRole = null;
            shopIdStr = null;
            userType = null;
        }

        if (userIdStr != null && !userIdStr.isEmpty()) {
            UserContext.setUserId(Long.parseLong(userIdStr));
        }
        if (username != null) {
            UserContext.setUsername(username);
        }
        if (userRole != null && !userRole.isEmpty()) {
            UserContext.setRole(userRole);
            // 兼容旧的 "admin" 字面量与新的 platform_admin
            if (CommonConstants.ROLE_PLATFORM_ADMIN.equals(userRole) || "admin".equals(userRole)) {
                UserContext.setIsAdmin(true);
            }
        }
        if (shopIdStr != null && !shopIdStr.isEmpty()) {
            UserContext.setShopId(Long.parseLong(shopIdStr));
        }
        if (userType != null && !userType.isEmpty()) {
            UserContext.setUserType(userType);
        }

        // 仅对 Controller 方法做注解级鉴权
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        // @InternalApi：服务间接口，只认内部凭证，不要求用户登录态。
        // 缺凭证一律 403 —— 这类接口（改库存、改商品、审核）默认应当关闭，
        // 仅凭"端口不可达"保护是不够的。
        if (hasAnnotation(handlerMethod, InternalApi.class)) {
            if (!InternalSignature.verifyInternal(internalSecret,
                    request.getHeader(CommonConstants.HEADER_INTERNAL_SIGNATURE))) {
                log.warn("内部接口缺少合法服务间凭证，拒绝访问: uri={}", request.getRequestURI());
                throw new BusinessException(ResultCode.FORBIDDEN);
            }
            return true;
        }

        // @Public：免登录放行（UserContext 已重建，内部 Feign 租户过滤不受影响）
        if (hasAnnotation(handlerMethod, Public.class)) {
            return true;
        }

        // 默认关闭：非 @Public 接口一律需登录
        if (UserContext.getUserId() == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }

        // @RequireAdmin：需平台管理员
        if (hasAnnotation(handlerMethod, RequireAdmin.class) && !UserContext.isAdmin()) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }

        // @RequireRole：需命中指定角色之一
        RequireRole requireRole = getAnnotation(handlerMethod, RequireRole.class);
        if (requireRole != null && !UserContext.hasAnyRole(requireRole.value())) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }

        // @RequirePerm：需拥有指定权限标识（含 * 通配）
        RequirePerm requirePerm = getAnnotation(handlerMethod, RequirePerm.class);
        if (requirePerm != null) {
            if (permissionChecker == null
                    || !permissionChecker.hasPermission(UserContext.getUserType(),
                            UserContext.getUserId(), requirePerm.value())) {
                throw new BusinessException(ResultCode.FORBIDDEN);
            }
        }

        return true;
    }

    private <A extends java.lang.annotation.Annotation> boolean hasAnnotation(HandlerMethod handlerMethod, Class<A> type) {
        return getAnnotation(handlerMethod, type) != null;
    }

    private <A extends java.lang.annotation.Annotation> A getAnnotation(HandlerMethod handlerMethod, Class<A> type) {
        A ann = handlerMethod.getMethodAnnotation(type);
        if (ann == null) {
            ann = handlerMethod.getBeanType().getAnnotation(type);
        }
        return ann;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        UserContext.clear();
    }
}
