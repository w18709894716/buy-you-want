package com.byw.admin.controller;

import com.byw.api.user.RbacFeignClient;
import com.byw.api.user.dto.SysMenuDTO;
import com.byw.common.core.result.R;
import com.byw.common.security.context.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 平台端：当前登录员工的菜单树与权限集（仅需登录，前端动态菜单/按钮权限）。
 */
@RestController
@RequestMapping("/admin/me")
@RequiredArgsConstructor
public class AdminMeController {

    private final RbacFeignClient rbacFeignClient;

    @GetMapping("/menus")
    public R<Map<String, Object>> menus() {
        Long userId = UserContext.getUserId();
        List<SysMenuDTO> menus = rbacFeignClient.getMenuTree("platform", 1, userId).getData();
        List<String> perms = rbacFeignClient.listPermCodes(1, userId).getData();
        Map<String, Object> data = new HashMap<>(4);
        data.put("menus", menus);
        data.put("perms", perms);
        return R.ok(data);
    }
}
