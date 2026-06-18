package com.byw.common.security.interceptor;

import com.byw.common.core.constant.CommonConstants;
import com.byw.common.security.context.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.HandlerInterceptor;

@Slf4j
public class AuthInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String userIdStr = request.getHeader(CommonConstants.HEADER_USER_ID);
        String username = request.getHeader(CommonConstants.HEADER_USERNAME);
        String userRole = request.getHeader(CommonConstants.HEADER_USER_ROLE);

        if (userIdStr != null && !userIdStr.isEmpty()) {
            UserContext.setUserId(Long.parseLong(userIdStr));
        }
        if (username != null) {
            UserContext.setUsername(username);
        }
        if ("admin".equals(userRole)) {
            UserContext.setIsAdmin(true);
        }

        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        UserContext.clear();
    }
}
