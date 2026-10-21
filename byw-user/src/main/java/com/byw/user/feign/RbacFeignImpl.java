package com.byw.user.feign;

import jakarta.validation.Valid;
import com.byw.common.log.annotation.OperationLog;
import com.byw.api.user.RbacFeignClient;
import com.byw.api.user.dto.SysMenuDTO;
import com.byw.api.user.dto.SysRoleDTO;
import com.byw.api.user.dto.SysUserDTO;
import com.byw.common.core.result.PageResult;
import com.byw.common.core.result.R;
import com.byw.common.security.annotation.InternalApi;
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
@InternalApi
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
    @OperationLog(module = "权限", operation = "新增系统用户", description = "新增后台系统用户")
    @PostMapping("/sys-user")
    public R<Long> createSysUser(@Valid @RequestBody SysUserDTO dto) {
        return R.ok(rbacService.createSysUser(dto));
    }

    @Override
    @OperationLog(module = "权限", operation = "修改系统用户", description = "修改后台系统用户")
    @PutMapping("/sys-user")
    public R<Boolean> updateSysUser(@Valid @RequestBody SysUserDTO dto) {
        return R.ok(rbacService.updateSysUser(dto));
    }

    @Override
    @OperationLog(module = "权限", operation = "用户状态", description = "启用/禁用系统用户")
    @PutMapping("/sys-user/{userId}/status")
    public R<Boolean> updateSysUserStatus(@PathVariable("userId") Long userId, @RequestParam("status") Integer status) {
        return R.ok(rbacService.updateSysUserStatus(userId, status));
    }

    @Override
    @OperationLog(module = "权限", operation = "重置密码", description = "重置系统用户密码")
    @PutMapping("/sys-user/{userId}/password")
    public R<Boolean> resetSysUserPassword(@PathVariable("userId") Long userId, @RequestParam("password") String password) {
        return R.ok(rbacService.resetSysUserPassword(userId, password));
    }

    @Override
    @OperationLog(module = "权限", operation = "分配角色", description = "给用户分配角色")
    @PostMapping("/user/{userId}/roles")
    public R<Boolean> assignRoles(@PathVariable("userId") Long userId,
                                  @RequestParam("userType") Integer userType,
                                  @RequestBody List<Long> roleIds) {
        return R.ok(rbacService.assignRoles(userId, userType, roleIds));
    }

    @Override
    @GetMapping("/user/{userId}/roles")
    public R<List<SysRoleDTO>> listUserRoles(@PathVariable("userId") Long userId,
                                             @RequestParam("userType") Integer userType) {
        return R.ok(rbacService.listUserRoles(userId, userType));
    }

    @Override
    @OperationLog(module = "权限", operation = "移除角色", description = "移除用户角色")
    @DeleteMapping("/user/{userId}/roles/{roleId}")
    public R<Boolean> unbindUserRole(@PathVariable("userId") Long userId,
                                     @RequestParam("userType") Integer userType,
                                     @PathVariable("roleId") Long roleId) {
        return R.ok(rbacService.unbindUserRole(userId, userType, roleId));
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
    @OperationLog(module = "权限", operation = "新增角色", description = "新增角色")
    @PostMapping("/role")
    public R<Long> createRole(@Valid @RequestBody SysRoleDTO dto) {
        return R.ok(rbacService.createRole(dto));
    }

    @Override
    @OperationLog(module = "权限", operation = "复制角色", description = "复制角色及其权限")
    @PostMapping("/role/copy/{sourceRoleId}")
    public R<Long> copyRole(@PathVariable("sourceRoleId") Long sourceRoleId, @Valid @RequestBody SysRoleDTO dto) {
        return R.ok(rbacService.copyRole(sourceRoleId, dto));
    }

    @Override
    @GetMapping("/role/{roleId}/user-ids")
    public R<List<Long>> listRoleUserIds(@PathVariable("roleId") Long roleId) {
        return R.ok(rbacService.listRoleUserIds(roleId));
    }

    @Override
    @OperationLog(module = "权限", operation = "修改角色", description = "修改角色")
    @PutMapping("/role")
    public R<Boolean> updateRole(@Valid @RequestBody SysRoleDTO dto) {
        return R.ok(rbacService.updateRole(dto));
    }

    @Override
    @OperationLog(module = "权限", operation = "删除角色", description = "删除角色")
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
    @OperationLog(module = "权限", operation = "角色授权", description = "设置角色菜单权限")
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
    @GetMapping("/user-ids-by-perm")
    public R<List<Long>> listUserIdsByPerm(@RequestParam("permCode") String permCode,
                                           @RequestParam("userType") Integer userType) {
        return R.ok(rbacService.listUserIdsByPerm(permCode, userType));
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

    @Override
    @GetMapping("/menu/all-tree")
    public R<List<SysMenuDTO>> getMenuTreeAll(@RequestParam("scope") String scope) {
        return R.ok(rbacService.getMenuTreeAll(scope));
    }

    @Override
    @OperationLog(module = "权限", operation = "新增菜单", description = "新增菜单")
    @PostMapping("/menu")
    public R<Long> createMenu(@Valid @RequestBody SysMenuDTO dto) {
        return R.ok(rbacService.createMenu(dto));
    }

    @Override
    @OperationLog(module = "权限", operation = "修改菜单", description = "修改菜单")
    @PutMapping("/menu")
    public R<Boolean> updateMenu(@Valid @RequestBody SysMenuDTO dto) {
        return R.ok(rbacService.updateMenu(dto));
    }

    @Override
    @OperationLog(module = "权限", operation = "删除菜单", description = "删除菜单")
    @DeleteMapping("/menu/{menuId}")
    public R<Boolean> deleteMenu(@PathVariable("menuId") Long menuId) {
        return R.ok(rbacService.deleteMenu(menuId));
    }
}
