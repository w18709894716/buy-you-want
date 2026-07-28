package com.byw.merchant.controller;

import com.byw.api.promotion.PromotionFeignClient;
import com.byw.api.promotion.dto.CouponDTO;
import com.byw.common.core.constant.CommonConstants;
import com.byw.common.core.result.PageResult;
import com.byw.common.core.result.R;
import com.byw.common.security.annotation.RequireRole;
import com.byw.common.security.context.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 商家端店铺优惠券：仅本店券（下游按 X-Shop-Id 过滤），创建时强制归属本店。
 */
@RestController
@RequestMapping("/merchant/coupon")
@RequireRole({CommonConstants.ROLE_MERCHANT_OWNER, CommonConstants.ROLE_MERCHANT_STAFF})
@RequiredArgsConstructor
public class MerchantCouponController {

    private final PromotionFeignClient promotionFeignClient;

    @GetMapping("/list")
    public R<PageResult<CouponDTO>> listCoupons(@RequestParam(defaultValue = "1") Integer pageNum,
                                                @RequestParam(defaultValue = "10") Integer pageSize,
                                                @RequestParam(required = false) Integer status) {
        return promotionFeignClient.listCoupons(pageNum, pageSize, status);
    }

    @GetMapping("/{couponId}")
    public R<CouponDTO> getCouponById(@PathVariable Long couponId) {
        return promotionFeignClient.getCouponById(couponId);
    }

    @PostMapping
    public R<Long> createCoupon(@RequestBody CouponDTO couponDTO) {
        // 店铺券：强制归属当前商家店铺
        couponDTO.setShopId(UserContext.getShopId());
        return promotionFeignClient.createCoupon(couponDTO);
    }

    @PutMapping("/{couponId}")
    public R<Boolean> updateCoupon(@PathVariable Long couponId, @RequestBody CouponDTO couponDTO) {
        couponDTO.setShopId(UserContext.getShopId());
        return promotionFeignClient.updateCoupon(couponId, couponDTO);
    }

    @DeleteMapping("/{couponId}")
    public R<Boolean> deleteCoupon(@PathVariable Long couponId) {
        return promotionFeignClient.deleteCoupon(couponId);
    }
}
