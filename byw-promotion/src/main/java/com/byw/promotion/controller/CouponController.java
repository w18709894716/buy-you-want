package com.byw.promotion.controller;

import com.byw.api.promotion.dto.UserCouponDTO;
import com.byw.common.core.result.R;
import com.byw.common.security.annotation.RequireLogin;
import com.byw.common.security.context.UserContext;
import com.byw.promotion.entity.Coupon;
import com.byw.promotion.service.CouponService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "优惠券", description = "优惠券管理")
@RestController
@RequestMapping("/coupon")
@RequiredArgsConstructor
public class CouponController {

    private final CouponService couponService;

    @Operation(summary = "获取可用优惠券列表")
    @GetMapping("/list")
    public R<List<Coupon>> list(@RequestParam(value = "newUser", required = false) Integer newUser) {
        return R.ok(couponService.listAvailable(newUser));
    }

    @Operation(summary = "领取优惠券")
    @PostMapping("/claim/{couponId}")
    @RequireLogin
    public R<Void> claim(@PathVariable Long couponId) {
        couponService.claimCoupon(UserContext.getUserId(), couponId);
        return R.ok();
    }

    @Operation(summary = "我的优惠券列表")
    @GetMapping("/my-coupons")
    @RequireLogin
    public R<List<UserCouponDTO>> myCoupons(@RequestParam(value = "status", defaultValue = "0") Integer status) {
        return R.ok(couponService.listUserCoupons(UserContext.getUserId(), status));
    }
}
