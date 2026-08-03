package com.byw.admin.controller;

import com.byw.api.user.RbacFeignClient;
import com.byw.api.user.dto.SysMenuDTO;
import com.byw.api.user.dto.SysRoleDTO;
import com.byw.common.core.result.R;
import com.byw.common.security.annotation.RequirePerm;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 平台端：系统角色管理（一期仅角色列表、角色-菜单授权，转发 byw-user）。
 */
@RestController
@RequestMapping("/admin/sys/role")
@RequirePerm("sys:role")
@RequiredArgsConstructor
public class AdminSysRoleController {

    private final RbacFeignClient rbacFeignClient;

    /** 平台角色列表 */
    @GetMapping("/list")
    public R<List<SysRoleDTO>> list() {
        return rbacFeignClient.listRoles("platform");
    }

    /** 新建平台角色 */
    @PostMapping
    public R<Long> create(@RequestBody SysRoleDTO dto) {
        dto.setScope("platform");
        dto.setShopId(null);
        dto.setIsPreset(0);
        return rbacFeignClient.createRole(dto);
    }

    /** 编辑平台角色（内置预设不可改） */
    @PutMapping
    public R<Boolean> update(@RequestBody SysRoleDTO dto) {
        return rbacFeignClient.updateRole(dto);
    }

    /** 删除平台角色（内置预设/超管不可删，有员工绑定则拒绝） */
    @DeleteMapping("/{roleId}")
    public R<Boolean> delete(@PathVariable Long roleId) {
        return rbacFeignClient.deleteRole(roleId);
    }

    /** 完整平台菜单树（用于授权勾选） */
    @GetMapping("/menu-tree")
    public R<List<SysMenuDTO>> menuTree() {
        return rbacFeignClient.getAllMenuTree("platform");
    }

    /** 角色当前已绑定的菜单ID */
    @GetMapping("/{roleId}/menu-ids")
    public R<List<Long>> menuIds(@PathVariable Long roleId) {
        return rbacFeignClient.getRoleMenuIds(roleId);
    }

    /** 绑定角色菜单（覆盖式） */
    @PostMapping("/{roleId}/menus")
    public R<Boolean> bindMenus(@PathVariable Long roleId, @RequestBody List<Long> menuIds) {
        return rbacFeignClient.bindRoleMenus(roleId, menuIds);
    }
}
