package com.byw.common.security.feign;

import com.byw.common.core.constant.CommonConstants;
import com.byw.common.security.context.UserContext;
import feign.RequestInterceptor;
import feign.RequestTemplate;

/**
 * Feign 调用间身份透传：将当前登录上下文（用户/角色/店铺）写入下游请求头，
 * 使下游服务的 AuthInterceptor 能重建 UserContext，从而实现按 shopId 的租户过滤。
 */
public class FeignAuthRelayInterceptor implements RequestInterceptor {

    @Override
    public void apply(RequestTemplate template) {
        Long userId = UserContext.getUserId();
        if (userId != null) {
            template.header(CommonConstants.HEADER_USER_ID, String.valueOf(userId));
        }
        String username = UserContext.getUsername();
        if (username != null) {
            template.header(CommonConstants.HEADER_USERNAME, username);
        }
        String role = UserContext.getRole();
        if (role != null) {
            template.header(CommonConstants.HEADER_USER_ROLE, role);
        }
        Long shopId = UserContext.getShopId();
        if (shopId != null) {
            template.header(CommonConstants.HEADER_SHOP_ID, String.valueOf(shopId));
        }
        String userType = UserContext.getUserType();
        if (userType != null) {
            template.header(CommonConstants.HEADER_USER_TYPE, userType);
        }
    }
}
