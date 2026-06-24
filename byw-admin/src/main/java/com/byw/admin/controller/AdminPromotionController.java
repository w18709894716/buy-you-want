package com.byw.admin.controller;

import com.byw.api.promotion.PromotionFeignClient;
import com.byw.api.promotion.PromotionFeignClient.SeckillActivityDTO;
import com.byw.api.promotion.dto.CouponDTO;
import com.byw.common.core.result.PageResult;
import com.byw.common.core.result.R;
import com.byw.common.security.annotation.RequireAdmin;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/promotion")
@RequireAdmin
@RequiredArgsConstructor
public class AdminPromotionController {

    private final PromotionFeignClient promotionFeignClient;

    // ========== 优惠券管理 ==========

    @GetMapping("/coupon/list")
    public R<PageResult<CouponDTO>> listCoupons(@RequestParam(defaultValue = "1") Integer pageNum,
                                               @RequestParam(defaultValue = "10") Integer pageSize,
                                               @RequestParam(required = false) Integer status) {
        return promotionFeignClient.listCoupons(pageNum, pageSize, status);
    }

    @GetMapping("/coupon/{couponId}")
    public R<CouponDTO> getCouponById(@PathVariable Long couponId) {
        return promotionFeignClient.getCouponById(couponId);
    }

    @PostMapping("/coupon")
    public R<Long> createCoupon(@RequestBody CouponDTO couponDTO) {
        return promotionFeignClient.createCoupon(couponDTO);
    }

    @PutMapping("/coupon/{couponId}")
    public R<Boolean> updateCoupon(@PathVariable Long couponId, @RequestBody CouponDTO couponDTO) {
        return promotionFeignClient.updateCoupon(couponId, couponDTO);
    }

    @DeleteMapping("/coupon/{couponId}")
    public R<Boolean> deleteCoupon(@PathVariable Long couponId) {
        return promotionFeignClient.deleteCoupon(couponId);
    }

    // ========== 秒杀活动管理 ==========

    @GetMapping("/seckill/list")
    public R<List<SeckillActivityDTO>> listSeckillActivities() {
        return promotionFeignClient.listSeckillActivities();
    }

    @GetMapping("/seckill/{activityId}")
    public R<SeckillActivityDTO> getSeckillActivityById(@PathVariable Long activityId) {
        return promotionFeignClient.getSeckillActivityById(activityId);
    }

    @PostMapping("/seckill")
    public R<Long> createSeckillActivity(@RequestBody SeckillActivityDTO activityDTO) {
        return promotionFeignClient.createSeckillActivity(activityDTO);
    }

    @PutMapping("/seckill/{activityId}")
    public R<Boolean> updateSeckillActivity(@PathVariable Long activityId, @RequestBody SeckillActivityDTO activityDTO) {
        return promotionFeignClient.updateSeckillActivity(activityId, activityDTO);
    }

    @DeleteMapping("/seckill/{activityId}")
    public R<Boolean> deleteSeckillActivity(@PathVariable Long activityId) {
        return promotionFeignClient.deleteSeckillActivity(activityId);
    }
}
