package com.byw.merchant.controller;

import com.byw.api.user.RbacFeignClient;
import com.byw.api.user.dto.SysMenuDTO;
import com.byw.common.core.constant.CommonConstants;
import com.byw.common.core.result.R;
import com.byw.common.security.annotation.RequireRole;
import com.byw.common.security.context.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 商家端：当前登录账号的菜单树与权限集。
 * 主账号（merchant_owner）拥有全部商家菜单与通配权限；子账号按其角色过滤。
 *
 * <p>限商家角色：下面的 else 分支把 UserContext 的 userId 当作商家子账号 id 去查 RBAC，
 * 而 C 端会员与商家账号的 id 来自不同的表、取值区间重叠，
 * 只要求"已登录"会让会员 token 拿到某个同 id 商家员工的菜单与权限集。
 */
@RestController
@RequestMapping("/merchant/me")
@RequireRole({CommonConstants.ROLE_MERCHANT_OWNER, CommonConstants.ROLE_MERCHANT_STAFF})
@RequiredArgsConstructor
public class MerchantMeController {

    /** 商家账号用户类型（RBAC user_role.user_type） */
    private static final Integer USER_TYPE_MERCHANT = 2;

    private final RbacFeignClient rbacFeignClient;

    @GetMapping("/menus")
    public R<Map<String, Object>> menus() {
        Long userId = UserContext.getUserId();
        List<SysMenuDTO> menus;
        List<String> perms;
        if (UserContext.hasAnyRole(CommonConstants.ROLE_MERCHANT_OWNER)) {
            // 主账号：全部商家菜单 + 通配权限
            menus = rbacFeignClient.getAllMenuTree("merchant").getData();
            perms = Collections.singletonList(CommonConstants.PERM_ALL);
        } else {
            // 子账号：按角色权限过滤
            menus = rbacFeignClient.getMenuTree("merchant", USER_TYPE_MERCHANT, userId).getData();
            perms = rbacFeignClient.listPermCodes(USER_TYPE_MERCHANT, userId).getData();
        }
        Map<String, Object> data = new HashMap<>(4);
        data.put("menus", menus);
        data.put("perms", perms);
        return R.ok(data);
    }
}
