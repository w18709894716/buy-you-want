package com.byw.merchant.controller;

import com.byw.api.shop.ShopFeignClient;
import com.byw.api.shop.dto.MerchantAccountDTO;
import com.byw.api.user.RbacFeignClient;
import com.byw.api.user.dto.SysRoleDTO;
import com.byw.common.core.result.PageResult;
import com.byw.common.core.result.R;
import com.byw.common.security.annotation.RequirePerm;
import com.byw.common.security.context.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.stream.Collectors;

/**
 * 商家端员工管理：主账号维护本店子账号（子账号复用 t_merchant_account，parent_id=主账号）。
 * 子账号 CRUD 转发 byw-shop；预设角色分配转发 byw-user RBAC 契约（user_type=2 商家账号）。
 */
@RestController
@RequestMapping("/merchant/staff")
@RequirePerm("m:staff:manage")
@RequiredArgsConstructor
public class MerchantStaffController {

    /** 商家账号用户类型（RBAC user_role.user_type） */
    private static final Integer USER_TYPE_MERCHANT = 2;

    /** 选项接口扫描子账号上限（单店子账号量级内一次拉全） */
    private static final int MAX_OPTIONS_SCAN = 200;

    private final ShopFeignClient shopFeignClient;
    private final RbacFeignClient rbacFeignClient;

    /** 本店子账号分页列表（BFF 聚合：补充每个员工绑定的角色，供列表展示与分配回填） */
    @GetMapping("/list")
    public R<PageResult<MerchantAccountDTO>> list(@RequestParam(defaultValue = "1") Integer pageNum,
                                                  @RequestParam(defaultValue = "10") Integer pageSize) {
        PageResult<MerchantAccountDTO> page = shopFeignClient.listStaff(UserContext.getUserId(), pageNum, pageSize).getData();
        if (page != null && page.getList() != null) {
            for (MerchantAccountDTO dto : page.getList()) {
                List<SysRoleDTO> roles = rbacFeignClient.listUserRoles(dto.getId(), USER_TYPE_MERCHANT).getData();
                if (roles != null) {
                    dto.setRoleIds(roles.stream().map(SysRoleDTO::getId).collect(Collectors.toList()));
                    dto.setRoleNames(roles.stream().map(SysRoleDTO::getRoleName).collect(Collectors.joining("、")));
                }
            }
        }
        return R.ok(page);
    }

    /** 新建子账号（用户名+密码+姓名+电话+角色），随后分配预设角色 */
    @PostMapping
    public R<Long> create(@RequestBody MerchantAccountDTO dto) {
        R<Long> created = shopFeignClient.createStaff(UserContext.getUserId(), UserContext.getShopId(), dto);
        Long staffId = created.getData();
        if (staffId != null && dto.getRoleIds() != null && !dto.getRoleIds().isEmpty()) {
            rbacFeignClient.assignRoles(staffId, USER_TYPE_MERCHANT, dto.getRoleIds());
        }
        return created;
    }

    /** 启停子账号 */
    @PutMapping("/{staffId}/status")
    public R<Void> updateStatus(@PathVariable Long staffId, @RequestParam Integer status) {
        return shopFeignClient.updateStaffStatus(staffId, UserContext.getUserId(), status);
    }

    /** 重置子账号密码 */
    @PutMapping("/{staffId}/password")
    public R<Void> resetPassword(@PathVariable Long staffId, @RequestParam String password) {
        return shopFeignClient.resetStaffPassword(staffId, UserContext.getUserId(), password);
    }

    /** 调整子账号预设角色（覆盖式） */
    @PostMapping("/{staffId}/roles")
    public R<Boolean> assignRoles(@PathVariable Long staffId, @RequestBody List<Long> roleIds) {
        return rbacFeignClient.assignRoles(staffId, USER_TYPE_MERCHANT, roleIds);
    }

    /** 商家可分配角色列表：平台预设模板 + 本店自定义角色（供分配下拉） */
    @GetMapping("/roles")
    public R<List<SysRoleDTO>> roles() {
        return rbacFeignClient.listMerchantRoles(UserContext.getShopId());
    }

    /**
     * 本店持有指定权限的启用子账号列表（供下拉选项，如客服分流选参与客服仅列 m:im:workbench 持有者）。
     * 方法级权限覆盖类级 m:staff:manage：客服分流页面（m:im:dispatch）也调用本接口拉取参与客服选项。
     * 按当前登录者店铺查子账号（不依赖调用者是否主账号，子账号登录也能拿到选项）。
     */
    @RequirePerm("m:im:dispatch")
    @GetMapping("/options")
    public R<List<MerchantAccountDTO>> options(@RequestParam("permCode") String permCode) {
        Long shopId = UserContext.getShopId();
        if (shopId == null) {
            return R.fail("仅商家端可用");
        }
        List<Long> holderIds = rbacFeignClient.listUserIdsByPerm(permCode, USER_TYPE_MERCHANT).getData();
        if (holderIds == null || holderIds.isEmpty()) {
            return R.ok(Collections.emptyList());
        }
        Set<Long> holderSet = new HashSet<>(holderIds);
        List<MerchantAccountDTO> staff = shopFeignClient
                .listActiveStaffByShop(shopId, MAX_OPTIONS_SCAN).getData();
        if (staff == null || staff.isEmpty()) {
            return R.ok(Collections.emptyList());
        }
        List<MerchantAccountDTO> options = staff.stream()
                .filter(dto -> holderSet.contains(dto.getId()))
                .collect(Collectors.toList());
        return R.ok(options);
    }
}
