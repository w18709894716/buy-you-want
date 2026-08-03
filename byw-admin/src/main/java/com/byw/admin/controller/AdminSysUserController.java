package com.byw.admin.controller;

import com.byw.api.user.RbacFeignClient;
import com.byw.api.user.dto.SysUserDTO;
import com.byw.common.core.result.PageResult;
import com.byw.common.core.result.R;
import com.byw.common.security.annotation.RequirePerm;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 平台端：系统员工管理（转发 byw-user 的 RBAC 契约）。
 */
@RestController
@RequestMapping("/admin/sys/user")
@RequirePerm("sys:user")
@RequiredArgsConstructor
public class AdminSysUserController {

    private final RbacFeignClient rbacFeignClient;

    @GetMapping("/page")
    public R<PageResult<SysUserDTO>> page(@RequestParam(defaultValue = "1") Integer pageNum,
                                          @RequestParam(defaultValue = "10") Integer pageSize,
                                          @RequestParam(required = false) String keyword) {
        return rbacFeignClient.getSysUserPage(pageNum, pageSize, keyword);
    }

    @PostMapping
    public R<Long> create(@RequestBody SysUserDTO dto) {
        return rbacFeignClient.createSysUser(dto);
    }

    @PutMapping
    public R<Boolean> update(@RequestBody SysUserDTO dto) {
        return rbacFeignClient.updateSysUser(dto);
    }

    @PutMapping("/{userId}/status")
    public R<Boolean> updateStatus(@PathVariable Long userId, @RequestParam Integer status) {
        return rbacFeignClient.updateSysUserStatus(userId, status);
    }

    @PutMapping("/{userId}/password")
    public R<Boolean> resetPassword(@PathVariable Long userId, @RequestParam String password) {
        return rbacFeignClient.resetSysUserPassword(userId, password);
    }

    /** 分配角色（平台员工 userType=1） */
    @PostMapping("/{userId}/roles")
    public R<Boolean> assignRoles(@PathVariable Long userId, @RequestBody List<Long> roleIds) {
        return rbacFeignClient.assignRoles(userId, 1, roleIds);
    }
}
