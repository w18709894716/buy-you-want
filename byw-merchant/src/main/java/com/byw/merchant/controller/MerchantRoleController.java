package com.byw.merchant.controller;

import com.byw.api.user.RbacFeignClient;
import com.byw.api.user.dto.SysMenuDTO;
import com.byw.api.user.dto.SysRoleDTO;
import com.byw.common.core.exception.BusinessException;
import com.byw.common.core.result.R;
import com.byw.common.security.annotation.RequirePerm;
import com.byw.common.security.context.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 商家端角色管理：主账号维护本店自定义角色（scope=merchant, shop_id=本店）。
 * 平台预设的商家角色模板（shop_id=NULL, is_preset=1）对商家只读；数据转发 byw-user RBAC 契约。
 */
@RestController
@RequestMapping("/merchant/role")
@RequirePerm("m:role:manage")
@RequiredArgsConstructor
public class MerchantRoleController {

    private static final String SCOPE_MERCHANT = "merchant";

    private final RbacFeignClient rbacFeignClient;

    /** 本店可见角色：平台预设模板 + 本店自定义角色 */
    @GetMapping("/list")
    public R<List<SysRoleDTO>> list() {
        return rbacFeignClient.listMerchantRoles(UserContext.getShopId());
    }

    /** 商家菜单树（用于授权勾选） */
    @GetMapping("/menu-tree")
    public R<List<SysMenuDTO>> menuTree() {
        return rbacFeignClient.getAllMenuTree(SCOPE_MERCHANT);
    }

    /** 新建本店自定义角色（roleCode 由后端自动生成） */
    @PostMapping
    public R<Long> create(@RequestBody SysRoleDTO dto) {
        dto.setScope(SCOPE_MERCHANT);
        dto.setShopId(UserContext.getShopId());
        dto.setIsPreset(0);
        dto.setRoleCode(null);
        return rbacFeignClient.createRole(dto);
    }

    /** 编辑本店自定义角色 */
    @PutMapping
    public R<Boolean> update(@RequestBody SysRoleDTO dto) {
        verifyOwn(dto.getId());
        return rbacFeignClient.updateRole(dto);
    }

    /** 删除本店自定义角色（有员工绑定则拒绝） */
    @DeleteMapping("/{roleId}")
    public R<Boolean> delete(@PathVariable Long roleId) {
        verifyOwn(roleId);
        return rbacFeignClient.deleteRole(roleId);
    }

    /** 角色当前已绑定的菜单ID（只读查看：本店自定义角色与平台预设模板均可查，便于商家比对预设权限） */
    @GetMapping("/{roleId}/menu-ids")
    public R<List<Long>> menuIds(@PathVariable Long roleId) {
        verifyViewable(roleId);
        return rbacFeignClient.getRoleMenuIds(roleId);
    }

    /** 绑定角色菜单（覆盖式） */
    @PostMapping("/{roleId}/menus")
    public R<Boolean> bindMenus(@PathVariable Long roleId, @RequestBody List<Long> menuIds) {
        verifyOwn(roleId);
        return rbacFeignClient.bindRoleMenus(roleId, menuIds);
    }

    /** 校验角色归属本店且非预设，防止越权操作他店角色或修改预设模板 */
    private void verifyOwn(Long roleId) {
        SysRoleDTO role = rbacFeignClient.getRole(roleId).getData();
        if (role == null || !SCOPE_MERCHANT.equals(role.getScope())
                || role.getShopId() == null || !role.getShopId().equals(UserContext.getShopId())) {
            throw new BusinessException("无权操作该角色");
        }
        if (role.getIsPreset() != null && role.getIsPreset() == 1) {
            throw new BusinessException("预设角色不可修改");
        }
    }

    /** 校验角色为本店可见（本店自定义角色或平台预设商家模板），允许只读查看其授权 */
    private void verifyViewable(Long roleId) {
        SysRoleDTO role = rbacFeignClient.getRole(roleId).getData();
        if (role == null || !SCOPE_MERCHANT.equals(role.getScope())) {
            throw new BusinessException("无权操作该角色");
        }
        boolean ownShop = role.getShopId() != null && role.getShopId().equals(UserContext.getShopId());
        boolean presetTemplate = role.getShopId() == null
                && role.getIsPreset() != null && role.getIsPreset() == 1;
        if (!ownShop && !presetTemplate) {
            throw new BusinessException("无权操作该角色");
        }
    }
}
