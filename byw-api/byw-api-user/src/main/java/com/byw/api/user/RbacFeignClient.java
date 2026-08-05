package com.byw.api.user;

import com.byw.api.user.dto.SysMenuDTO;
import com.byw.api.user.dto.SysRoleDTO;
import com.byw.api.user.dto.SysUserDTO;
import com.byw.common.core.result.PageResult;
import com.byw.common.core.result.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * RBAC 契约：平台员工、角色、菜单权限的数据操作，目标服务 byw-user（数据持有方）。
 * 平台端 byw-admin / 商家端 byw-merchant BFF 经此转发。
 */
@FeignClient(name = "byw-user", contextId = "rbacFeignClient")
public interface RbacFeignClient {

    // ===== 平台员工 =====

    @GetMapping("/feign/rbac/sys-user/username/{username}")
    R<SysUserDTO> getSysUserByUsername(@PathVariable("username") String username);

    @GetMapping("/feign/rbac/sys-user/page")
    R<PageResult<SysUserDTO>> getSysUserPage(@RequestParam(value = "pageNum", defaultValue = "1") Integer pageNum,
                                             @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize,
                                             @RequestParam(value = "keyword", required = false) String keyword);

    @PostMapping("/feign/rbac/sys-user")
    R<Long> createSysUser(@RequestBody SysUserDTO dto);

    @PutMapping("/feign/rbac/sys-user")
    R<Boolean> updateSysUser(@RequestBody SysUserDTO dto);

    @PutMapping("/feign/rbac/sys-user/{userId}/status")
    R<Boolean> updateSysUserStatus(@PathVariable("userId") Long userId, @RequestParam("status") Integer status);

    @PutMapping("/feign/rbac/sys-user/{userId}/password")
    R<Boolean> resetSysUserPassword(@PathVariable("userId") Long userId, @RequestParam("password") String password);

    /** 为用户分配角色（覆盖式）。userType：1平台员工 2商家账号 */
    @PostMapping("/feign/rbac/user/{userId}/roles")
    R<Boolean> assignRoles(@PathVariable("userId") Long userId,
                           @RequestParam("userType") Integer userType,
                           @RequestBody List<Long> roleIds);

    /** 查询用户绑定的角色列表（含角色名）。userType：1平台员工 2商家账号 */
    @GetMapping("/feign/rbac/user/{userId}/roles")
    R<List<SysRoleDTO>> listUserRoles(@PathVariable("userId") Long userId,
                                      @RequestParam("userType") Integer userType);

    /** 解绑用户与角色的绑定关系。userType：1平台员工 2商家账号 */
    @DeleteMapping("/feign/rbac/user/{userId}/roles/{roleId}")
    R<Boolean> unbindUserRole(@PathVariable("userId") Long userId,
                              @RequestParam("userType") Integer userType,
                              @PathVariable("roleId") Long roleId);

    // ===== 角色 =====

    @GetMapping("/feign/rbac/role/list")
    R<List<SysRoleDTO>> listRoles(@RequestParam("scope") String scope);

    /** 商家可见角色：平台预设模板 + 本店自定义角色 */
    @GetMapping("/feign/rbac/role/merchant-list")
    R<List<SysRoleDTO>> listMerchantRoles(@RequestParam("shopId") Long shopId);

    @GetMapping("/feign/rbac/role/{roleId}")
    R<SysRoleDTO> getRole(@PathVariable("roleId") Long roleId);

    @PostMapping("/feign/rbac/role")
    R<Long> createRole(@RequestBody SysRoleDTO dto);

    /** 复制角色（含预设模板）：创建自定义角色并继承源角色菜单授权 */
    @PostMapping("/feign/rbac/role/copy/{sourceRoleId}")
    R<Long> copyRole(@PathVariable("sourceRoleId") Long sourceRoleId, @RequestBody SysRoleDTO dto);

    /** 查询绑定指定角色的用户ID列表（商家账号 user_type=2） */
    @GetMapping("/feign/rbac/role/{roleId}/user-ids")
    R<List<Long>> listRoleUserIds(@PathVariable("roleId") Long roleId);

    @PutMapping("/feign/rbac/role")
    R<Boolean> updateRole(@RequestBody SysRoleDTO dto);

    @DeleteMapping("/feign/rbac/role/{roleId}")
    R<Boolean> deleteRole(@PathVariable("roleId") Long roleId);

    @GetMapping("/feign/rbac/role/{roleId}/menu-ids")
    R<List<Long>> getRoleMenuIds(@PathVariable("roleId") Long roleId);

    @PostMapping("/feign/rbac/role/{roleId}/menus")
    R<Boolean> bindRoleMenus(@PathVariable("roleId") Long roleId, @RequestBody List<Long> menuIds);

    // ===== 权限 / 菜单 =====

    /** 聚合权限标识（超级管理员返回 ["*"]）。userType：1平台员工 2商家账号 */
    @GetMapping("/feign/rbac/perms")
    R<List<String>> listPermCodes(@RequestParam("userType") Integer userType,
                                  @RequestParam("userId") Long userId);

    /** 按用户权限过滤后的菜单树（用于前端动态菜单渲染） */
    @GetMapping("/feign/rbac/menu/tree")
    R<List<SysMenuDTO>> getMenuTree(@RequestParam("scope") String scope,
                                    @RequestParam("userType") Integer userType,
                                    @RequestParam("userId") Long userId);

    /** 指定 scope 的完整菜单树（用于角色授权配置） */
    @GetMapping("/feign/rbac/menu/all")
    R<List<SysMenuDTO>> getAllMenuTree(@RequestParam("scope") String scope);

    // ===== 菜单管理（平台端动态维护两端菜单树） =====

    /** 指定 scope 的全量菜单树（含停用，用于菜单管理页） */
    @GetMapping("/feign/rbac/menu/all-tree")
    R<List<SysMenuDTO>> getMenuTreeAll(@RequestParam("scope") String scope);

    @PostMapping("/feign/rbac/menu")
    R<Long> createMenu(@RequestBody SysMenuDTO dto);

    @PutMapping("/feign/rbac/menu")
    R<Boolean> updateMenu(@RequestBody SysMenuDTO dto);

    @DeleteMapping("/feign/rbac/menu/{menuId}")
    R<Boolean> deleteMenu(@PathVariable("menuId") Long menuId);
}
