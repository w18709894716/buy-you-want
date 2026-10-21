package com.byw.common.security.feign;

import com.byw.common.core.constant.CommonConstants;
import com.byw.common.security.context.UserContext;
import com.byw.common.security.sign.InternalSignature;
import feign.RequestInterceptor;
import feign.RequestTemplate;

/**
 * Feign 调用间身份透传：将当前登录上下文（用户/角色/店铺）写入下游请求头，并加盖与服务端
 * 校验一致的签名，使下游服务的 AuthInterceptor 能重建 UserContext，从而实现按 shopId 的租户过滤。
 */
public class FeignAuthRelayInterceptor implements RequestInterceptor {

    private final String internalSecret;

    public FeignAuthRelayInterceptor(String internalSecret) {
        this.internalSecret = internalSecret;
    }

    @Override
    public void apply(RequestTemplate template) {
        // 服务间凭证与用户身份无关：库存回补、结算等后台链路没有 UserContext，也必须带凭证，
        // 否则被 @InternalApi 拦下（这些接口不再靠 @Public 敞开）。
        template.header(CommonConstants.HEADER_INTERNAL_SIGNATURE, InternalSignature.signInternal(internalSecret));

        Long userId = UserContext.getUserId();
        String username = UserContext.getUsername();
        String role = UserContext.getRole();
        Long shopId = UserContext.getShopId();
        String userType = UserContext.getUserType();
        if (userId == null && username == null && role == null && shopId == null && userType == null) {
            return;
        }

        String userIdValue = userId == null ? null : String.valueOf(userId);
        String shopIdValue = shopId == null ? null : String.valueOf(shopId);

        if (userIdValue != null) {
            template.header(CommonConstants.HEADER_USER_ID, userIdValue);
        }
        if (username != null) {
            // 与网关一致：头里写百分号编码，签名（下方）用 UserContext 里的原文
            template.header(CommonConstants.HEADER_USERNAME, InternalSignature.encodeUsername(username));
        }
        if (role != null) {
            template.header(CommonConstants.HEADER_USER_ROLE, role);
        }
        if (shopIdValue != null) {
            template.header(CommonConstants.HEADER_SHOP_ID, shopIdValue);
        }
        if (userType != null) {
            template.header(CommonConstants.HEADER_USER_TYPE, userType);
        }
        template.header(CommonConstants.HEADER_IDENTITY_SIGNATURE,
                InternalSignature.sign(internalSecret, userIdValue, username, role, shopIdValue, userType));
    }
}
