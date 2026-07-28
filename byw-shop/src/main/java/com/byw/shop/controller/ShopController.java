package com.byw.shop.controller;

import com.byw.api.shop.dto.MerchantAccountDTO;
import com.byw.api.shop.dto.ShopDTO;
import com.byw.common.core.result.PageResult;
import com.byw.common.core.result.R;
import com.byw.common.security.annotation.Public;
import com.byw.common.security.annotation.RequireAdmin;
import com.byw.common.security.annotation.RequireRole;
import com.byw.common.core.constant.CommonConstants;
import com.byw.common.security.context.UserContext;
import com.byw.shop.entity.MerchantAccount;
import com.byw.shop.service.ShopService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/shop")
@RequiredArgsConstructor
public class ShopController {

    private final ShopService shopService;

    /**
     * 商家入驻申请（免登录；商家身份独立于C端账号，归属与防重复以申请账号 username 为键）
     */
    @Public
    @PostMapping("/merchant/apply")
    public R<Long> applyMerchant(@RequestBody MerchantAccountDTO dto) {
        MerchantAccount account = new MerchantAccount();
        BeanUtils.copyProperties(dto, account);
        return R.ok(shopService.applyMerchant(account));
    }

    /**
     * 查询入驻申请进度（免登录，凭申请时设置的商家账号+密码验证归属；无记录或密码不匹配返回 null）
     */
    @Public
    @PostMapping("/merchant/apply-query")
    public R<MerchantAccountDTO> queryApply(@RequestBody MerchantAccountDTO dto) {
        return R.ok(shopService.getApplyByAccount(dto.getUsername(), dto.getPassword()));
    }

    /**
     * 查询店铺公开信息
     */
    @GetMapping("/{shopId}")
    @Public
    public R<ShopDTO> getShop(@PathVariable("shopId") Long shopId) {
        return R.ok(shopService.getShopById(shopId));
    }

    /**
     * 平台端：分页查询商家入驻申请
     */
    @RequireAdmin
    @GetMapping("/admin/merchant/list")
    public R<PageResult<MerchantAccountDTO>> listMerchants(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) Integer auditStatus) {
        return R.ok(shopService.listMerchants(pageNum, pageSize, auditStatus));
    }

    /**
     * 平台端：审核通过
     */
    @RequireAdmin
    @PostMapping("/admin/merchant/{merchantId}/approve")
    public R<Void> approveMerchant(@PathVariable("merchantId") Long merchantId,
                                   @RequestParam(required = false) String shopName) {
        shopService.approveMerchant(merchantId, shopName);
        return R.ok();
    }

    /**
     * 平台端：审核驳回
     */
    @RequireAdmin
    @PostMapping("/admin/merchant/{merchantId}/reject")
    public R<Void> rejectMerchant(@PathVariable("merchantId") Long merchantId,
                                  @RequestParam(required = false) String rejectReason) {
        shopService.rejectMerchant(merchantId, rejectReason);
        return R.ok();
    }

    /**
     * 平台端：分页查询店铺
     */
    @RequireAdmin
    @GetMapping("/admin/list")
    public R<PageResult<ShopDTO>> listShops(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) Integer status) {
        return R.ok(shopService.listShops(pageNum, pageSize, status));
    }

    /**
     * 平台端：更新店铺状态
     */
    @RequireAdmin
    @PutMapping("/admin/{shopId}/status")
    public R<Void> updateShopStatus(@PathVariable("shopId") Long shopId,
                                    @RequestParam("status") Integer status) {
        shopService.updateShopStatus(shopId, status);
        return R.ok();
    }

    /**
     * 商家端：更新自己的店铺信息（强制作用于当前登录商家的 shopId）
     */
    @RequireRole({CommonConstants.ROLE_MERCHANT_OWNER, CommonConstants.ROLE_MERCHANT_STAFF})
    @PutMapping("/merchant/update")
    public R<Void> updateShop(@RequestBody ShopDTO shopDTO) {
        shopDTO.setId(UserContext.getShopId());
        shopService.updateShop(shopDTO);
        return R.ok();
    }
}
