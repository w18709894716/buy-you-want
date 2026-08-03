package com.byw.user.feign;

import com.byw.api.user.RbacFeignClient;
import com.byw.api.user.dto.SysMenuDTO;
import com.byw.api.user.dto.SysRoleDTO;
import com.byw.api.user.dto.SysUserDTO;
import com.byw.common.core.result.PageResult;
import com.byw.common.core.result.R;
import com.byw.common.security.annotation.Public;
import com.byw.user.service.RbacService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * RBAC Feign 实现（数据持有方），暴露 /feign/rbac/**，供 byw-admin / byw-merchant / byw-auth 调用。
 */
@RestController
@RequestMapping("/feign/rbac")
@RequiredArgsConstructor
@Public
public class RbacFeignImpl implements RbacFeignClient {

    private final RbacService rbacService;

    @Override
    @GetMapping("/sys-user/username/{username}")
    public R<SysUserDTO> getSysUserByUsername(@PathVariable("username") String username) {
        return R.ok(rbacService.getSysUserByUsername(username));
    }

    @Override
    @GetMapping("/sys-user/page")
    public R<PageResult<SysUserDTO>> getSysUserPage(@RequestParam(value = "pageNum", defaultValue = "1") Integer pageNum,
                                                    @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize,
                                                    @RequestParam(value = "keyword", required = false) String keyword) {
        return R.ok(rbacService.getSysUserPage(pageNum, pageSize, keyword));
    }

    @Override
    @PostMapping("/sys-user")
    public R<Long> createSysUser(@RequestBody SysUserDTO dto) {
        return R.ok(rbacService.createSysUser(dto));
    }

    @Override
    @PutMapping("/sys-user")
    public R<Boolean> updateSysUser(@RequestBody SysUserDTO dto) {
        return R.ok(rbacService.updateSysUser(dto));
    }

    @Override
    @PutMapping("/sys-user/{userId}/status")
    public R<Boolean> updateSysUserStatus(@PathVariable("userId") Long userId, @RequestParam("status") Integer status) {
        return R.ok(rbacService.updateSysUserStatus(userId, status));
    }

    @Override
    @PutMapping("/sys-user/{userId}/password")
    public R<Boolean> resetSysUserPassword(@PathVariable("userId") Long userId, @RequestParam("password") String password) {
        return R.ok(rbacService.resetSysUserPassword(userId, password));
    }

    @Override
    @PostMapping("/user/{userId}/roles")
    public R<Boolean> assignRoles(@PathVariable("userId") Long userId,
                                  @RequestParam("userType") Integer userType,
                                  @RequestBody List<Long> roleIds) {
        return R.ok(rbacService.assignRoles(userId, userType, roleIds));
    }

    @Override
    @GetMapping("/role/list")
    public R<List<SysRoleDTO>> listRoles(@RequestParam("scope") String scope) {
        return R.ok(rbacService.listRoles(scope));
    }

    @Override
    @GetMapping("/role/merchant-list")
    public R<List<SysRoleDTO>> listMerchantRoles(@RequestParam("shopId") Long shopId) {
        return R.ok(rbacService.listMerchantRoles(shopId));
    }

    @Override
    @GetMapping("/role/{roleId}")
    public R<SysRoleDTO> getRole(@PathVariable("roleId") Long roleId) {
        return R.ok(rbacService.getRole(roleId));
    }

    @Override
    @PostMapping("/role")
    public R<Long> createRole(@RequestBody SysRoleDTO dto) {
        return R.ok(rbacService.createRole(dto));
    }

    @Override
    @PutMapping("/role")
    public R<Boolean> updateRole(@RequestBody SysRoleDTO dto) {
        return R.ok(rbacService.updateRole(dto));
    }

    @Override
    @DeleteMapping("/role/{roleId}")
    public R<Boolean> deleteRole(@PathVariable("roleId") Long roleId) {
        return R.ok(rbacService.deleteRole(roleId));
    }

    @Override
    @GetMapping("/role/{roleId}/menu-ids")
    public R<List<Long>> getRoleMenuIds(@PathVariable("roleId") Long roleId) {
        return R.ok(rbacService.getRoleMenuIds(roleId));
    }

    @Override
    @PostMapping("/role/{roleId}/menus")
    public R<Boolean> bindRoleMenus(@PathVariable("roleId") Long roleId, @RequestBody List<Long> menuIds) {
        return R.ok(rbacService.bindRoleMenus(roleId, menuIds));
    }

    @Override
    @GetMapping("/perms")
    public R<List<String>> listPermCodes(@RequestParam("userType") Integer userType,
                                         @RequestParam("userId") Long userId) {
        return R.ok(rbacService.listPermCodes(userType, userId));
    }

    @Override
    @GetMapping("/menu/tree")
    public R<List<SysMenuDTO>> getMenuTree(@RequestParam("scope") String scope,
                                           @RequestParam("userType") Integer userType,
                                           @RequestParam("userId") Long userId) {
        return R.ok(rbacService.getMenuTree(scope, userType, userId));
    }

    @Override
    @GetMapping("/menu/all")
    public R<List<SysMenuDTO>> getAllMenuTree(@RequestParam("scope") String scope) {
        return R.ok(rbacService.getAllMenuTree(scope));
    }
}
