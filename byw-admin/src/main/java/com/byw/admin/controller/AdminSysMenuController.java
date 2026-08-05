package com.byw.admin.controller;

import com.byw.api.user.RbacFeignClient;
import com.byw.api.user.dto.SysMenuDTO;
import com.byw.common.core.result.R;
import com.byw.common.security.annotation.RequirePerm;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 平台端：菜单管理（二期）。按 scope 动态维护平台/商家两套菜单树，转发 byw-user。
 */
@RestController
@RequestMapping("/admin/sys/menu")
@RequirePerm("sys:menu")
@RequiredArgsConstructor
public class AdminSysMenuController {

    private final RbacFeignClient rbacFeignClient;

    /** 指定 scope 的全量菜单树（含停用菜单） */
    @GetMapping("/tree")
    public R<List<SysMenuDTO>> tree(@RequestParam("scope") String scope) {
        return rbacFeignClient.getMenuTreeAll(scope);
    }

    /** 新增菜单（目录/菜单/按钮） */
    @PostMapping
    public R<Long> create(@RequestBody SysMenuDTO dto) {
        return rbacFeignClient.createMenu(dto);
    }

    /** 编辑菜单（类型不可变更；permCode 变更时联动清理权限缓存） */
    @PutMapping
    public R<Boolean> update(@RequestBody SysMenuDTO dto) {
        return rbacFeignClient.updateMenu(dto);
    }

    /** 删除菜单（存在子菜单或已被角色绑定则拒绝） */
    @DeleteMapping("/{menuId}")
    public R<Boolean> delete(@PathVariable Long menuId) {
        return rbacFeignClient.deleteMenu(menuId);
    }
}
