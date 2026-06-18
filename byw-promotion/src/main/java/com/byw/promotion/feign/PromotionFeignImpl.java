package com.byw.promotion.feign;

import com.byw.api.promotion.PromotionFeignClient;
import com.byw.api.promotion.dto.CouponDTO;
import com.byw.common.core.result.R;
import com.byw.promotion.service.CouponService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequestMapping("/feign/promotion")
@RequiredArgsConstructor
public class PromotionFeignImpl implements PromotionFeignClient {

    private final CouponService couponService;

    @Override
    @PostMapping("/coupon/use")
    public R<BigDecimal> useCoupon(@RequestParam("couponId") Long couponId,
                                    @RequestParam("userId") Long userId,
                                    @RequestParam("orderAmount") BigDecimal orderAmount) {
        return R.ok(couponService.useCoupon(couponId, userId, orderAmount));
    }

    @Override
    @PostMapping("/coupon/release")
    public R<Boolean> releaseCoupon(@RequestParam("couponId") Long couponId,
                                     @RequestParam("userId") Long userId) {
        couponService.releaseCoupon(couponId, userId);
        return R.ok(true);
    }

    @Override
    @GetMapping("/coupon/{couponId}")
    public R<CouponDTO> getCouponById(@PathVariable("couponId") Long couponId) {
        return R.ok(couponService.getCouponById(couponId));
    }
}
