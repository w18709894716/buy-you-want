package com.byw.merchant.controller;

import com.byw.api.shop.ShopFeignClient;
import com.byw.api.shop.dto.MerchantAccountDTO;
import com.byw.api.user.RbacFeignClient;
import com.byw.api.user.dto.SysMenuDTO;
import com.byw.api.user.dto.SysRoleDTO;
import com.byw.common.core.exception.BusinessException;
import com.byw.common.core.result.R;
import com.byw.common.security.annotation.RequirePerm;
import com.byw.common.security.context.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

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
    /** user_type：商家账号 */
    private static final int USER_TYPE_MERCHANT = 2;

    private final RbacFeignClient rbacFeignClient;
    private final ShopFeignClient shopFeignClient;

    /** 本店可见角色：平台预设模板 + 本店自定义角色 */
    @GetMapping("/list")
    public R<List<SysRoleDTO>> list() {
        Long shopId = UserContext.getShopId();
        List<SysRoleDTO> roles = rbacFeignClient.listMerchantRoles(shopId).getData();
        if (roles != null && !roles.isEmpty()) {
            // 逐个角色获取 userIds，收集所有 userId 去重用于批量查询
            Map<Long, List<Long>> roleUserMap = new HashMap<>();
            Set<Long> allUserIds = new HashSet<>();
            for (SysRoleDTO role : roles) {
                List<Long> userIds = rbacFeignClient.listRoleUserIds(role.getId()).getData();
                if (userIds != null) {
                    roleUserMap.put(role.getId(), userIds);
                    allUserIds.addAll(userIds);
                }
            }
            // 批量查询员工信息，过滤本店后覆盖 userCount
            if (!allUserIds.isEmpty()) {
                List<MerchantAccountDTO> members = shopFeignClient.getMerchantsByIds(new ArrayList<>(allUserIds)).getData();
                Set<Long> shopMemberIds = members == null ? Collections.emptySet()
                        : members.stream().filter(m -> m.getShopId() != null && m.getShopId().equals(shopId))
                        .map(MerchantAccountDTO::getId).collect(Collectors.toSet());
                for (SysRoleDTO role : roles) {
                    List<Long> userIds = roleUserMap.getOrDefault(role.getId(), Collections.emptyList());
                    long count = userIds.stream().filter(shopMemberIds::contains).count();
                    role.setUserCount((int) count);
                }
            }
        }
        return R.ok(roles);
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

    /** 复制角色（含预设模板）：以源角色为蓝本创建本店自定义角色并继承其菜单授权 */
    @PostMapping("/{roleId}/copy")
    public R<Long> copy(@PathVariable Long roleId, @RequestBody SysRoleDTO dto) {
        verifyViewable(roleId);
        dto.setScope(SCOPE_MERCHANT);
        dto.setShopId(UserContext.getShopId());
        dto.setIsPreset(0);
        dto.setRoleCode(null);
        return rbacFeignClient.copyRole(roleId, dto);
    }

    /** 角色成员列表（仅本店员工，不含跨店绑定） */
    @GetMapping("/{roleId}/members")
    public R<List<MerchantAccountDTO>> members(@PathVariable Long roleId) {
        verifyViewable(roleId);
        List<Long> userIds = rbacFeignClient.listRoleUserIds(roleId).getData();
        if (userIds == null || userIds.isEmpty()) {
            return R.ok(new ArrayList<>());
        }
        List<MerchantAccountDTO> all = shopFeignClient.getMerchantsByIds(userIds).getData();
        if (all == null) {
            return R.ok(new ArrayList<>());
        }
        Long shopId = UserContext.getShopId();
        List<MerchantAccountDTO> filtered = all.stream()
                .filter(m -> m.getShopId() != null && m.getShopId().equals(shopId))
                .toList();
        return R.ok(filtered);
    }

    /** 解绑角色成员（仅本店自定义角色） */
    @DeleteMapping("/{roleId}/members/{userId}")
    public R<Boolean> unbindMember(@PathVariable Long roleId, @PathVariable Long userId) {
        verifyOwn(roleId);
        return rbacFeignClient.unbindUserRole(userId, USER_TYPE_MERCHANT, roleId);
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
