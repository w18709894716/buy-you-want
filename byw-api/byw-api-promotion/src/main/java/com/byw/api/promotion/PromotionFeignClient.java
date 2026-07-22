package com.byw.api.promotion;

import com.byw.api.promotion.dto.CouponDTO;
import com.byw.api.promotion.dto.SeckillActivityDetailDTO;
import com.byw.api.promotion.dto.UserCouponDTO;
import com.byw.common.core.result.PageResult;
import com.byw.common.core.result.R;
import lombok.Data;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

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

    @GetMapping("/feign/promotion/coupon/user-coupons")
    R<List<UserCouponDTO>> listUserCoupons(@RequestParam("userId") Long userId,
                                           @RequestParam(value = "status", defaultValue = "0") Integer status);

    // ========== 管理后台接口 ==========

    @GetMapping("/feign/promotion/coupon/list")
    R<PageResult<CouponDTO>> listCoupons(@RequestParam(value = "pageNum", defaultValue = "1") Integer pageNum,
                                         @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize,
                                         @RequestParam(value = "status", required = false) Integer status);

    @PostMapping("/feign/promotion/coupon")
    R<Long> createCoupon(@RequestBody CouponDTO couponDTO);

    @PutMapping("/feign/promotion/coupon/{couponId}")
    R<Boolean> updateCoupon(@PathVariable("couponId") Long couponId, @RequestBody CouponDTO couponDTO);

    @DeleteMapping("/feign/promotion/coupon/{couponId}")
    R<Boolean> deleteCoupon(@PathVariable("couponId") Long couponId);

    @GetMapping("/feign/promotion/seckill/list")
    R<List<SeckillActivityDTO>> listSeckillActivities();

    @GetMapping("/feign/promotion/seckill/{activityId}")
    R<SeckillActivityDTO> getSeckillActivityById(@PathVariable("activityId") Long activityId);

    @PostMapping("/feign/promotion/seckill")
    R<Long> createSeckillActivity(@RequestBody SeckillActivityDTO activityDTO);

    @PutMapping("/feign/promotion/seckill/{activityId}")
    R<Boolean> updateSeckillActivity(@PathVariable("activityId") Long activityId, @RequestBody SeckillActivityDTO activityDTO);

    @DeleteMapping("/feign/promotion/seckill/{activityId}")
    R<Boolean> deleteSeckillActivity(@PathVariable("activityId") Long activityId);

    @GetMapping("/feign/promotion/seckill/list/detail")
    R<List<SeckillActivityDetailDTO>> listSeckillActivitiesWithProduct();

    @Data
    class SeckillActivityDTO implements Serializable {
        private Long id;
        private String name;
        private java.time.LocalDateTime startTime;
        private java.time.LocalDateTime endTime;
        private Integer status;
        private List<SeckillItemDTO> items;
    }

    @Data
    class SeckillItemDTO implements Serializable {
        private Long id;
        private Long productId;
        private Long skuId;
        private BigDecimal seckillPrice;
        private Integer totalStock;
        private Integer availableStock;
    }
}
