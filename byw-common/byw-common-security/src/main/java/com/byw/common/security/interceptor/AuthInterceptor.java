package com.byw.common.security.interceptor;

import com.byw.common.core.constant.CommonConstants;
import com.byw.common.core.exception.BusinessException;
import com.byw.common.core.exception.ResultCode;
import com.byw.common.security.annotation.Public;
import com.byw.common.security.annotation.RequireAdmin;
import com.byw.common.security.annotation.RequirePerm;
import com.byw.common.security.annotation.RequireRole;
import com.byw.common.security.context.UserContext;
import com.byw.common.security.service.PermissionChecker;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

@Slf4j
public class AuthInterceptor implements HandlerInterceptor {

    private final PermissionChecker permissionChecker;

    public AuthInterceptor() {
        this(null);
    }

    public AuthInterceptor(PermissionChecker permissionChecker) {
        this.permissionChecker = permissionChecker;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String userIdStr = request.getHeader(CommonConstants.HEADER_USER_ID);
        String username = request.getHeader(CommonConstants.HEADER_USERNAME);
        String userRole = request.getHeader(CommonConstants.HEADER_USER_ROLE);
        String shopIdStr = request.getHeader(CommonConstants.HEADER_SHOP_ID);
        String userType = request.getHeader(CommonConstants.HEADER_USER_TYPE);

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
