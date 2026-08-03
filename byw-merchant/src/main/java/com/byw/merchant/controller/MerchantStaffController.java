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

import java.util.List;
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
}
