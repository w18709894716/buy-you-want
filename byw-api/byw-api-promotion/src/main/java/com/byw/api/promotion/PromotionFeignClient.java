package com.byw.api.promotion;

import com.byw.api.promotion.dto.CouponDTO;
import com.byw.common.core.result.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@FeignClient(name = "byw-promotion", contextId = "promotionFeignClient")
public interface PromotionFeignClient {

    @PostMapping("/feign/promotion/coupon/use")
    R<BigDecimal> useCoupon(@RequestParam("couponId") Long couponId,
                            @RequestParam("userId") Long userId,
                            @RequestParam("orderAmount") BigDecimal orderAmount);

    @PostMapping("/feign/promotion/coupon/release")
    R<Boolean> releaseCoupon(@RequestParam("couponId") Long couponId,
                             @RequestParam("userId") Long userId);

    @GetMapping("/feign/promotion/coupon/{couponId}")
    R<CouponDTO> getCouponById(@PathVariable("couponId") Long couponId);
}
