package com.byw.promotion.feign;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.byw.api.promotion.PromotionFeignClient;
import com.byw.api.promotion.dto.CouponDTO;
import com.byw.common.core.result.PageResult;
import com.byw.common.core.result.R;
import com.byw.promotion.entity.Coupon;
import com.byw.promotion.entity.SeckillActivity;
import com.byw.promotion.mapper.CouponMapper;
import com.byw.promotion.mapper.SeckillActivityMapper;
import com.byw.promotion.service.CouponService;
import com.byw.promotion.service.SeckillService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/feign/promotion")
@RequiredArgsConstructor
public class PromotionFeignImpl implements PromotionFeignClient {

    private final CouponService couponService;
    private final SeckillService seckillService;
    private final CouponMapper couponMapper;
    private final SeckillActivityMapper seckillActivityMapper;

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

    @Override
    @GetMapping("/coupon/list")
    public R<PageResult<CouponDTO>> listCoupons(@RequestParam(value = "pageNum", defaultValue = "1") Integer pageNum,
                                                @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize,
                                                @RequestParam(value = "status", required = false) Integer status) {
        LambdaQueryWrapper<Coupon> wrapper = new LambdaQueryWrapper<>();
        if (status != null) {
            wrapper.eq(Coupon::getStatus, status);
        }
        wrapper.orderByDesc(Coupon::getCreatedAt);
        IPage<Coupon> page = couponMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        List<CouponDTO> dtoList = page.getRecords().stream().map(coupon -> {
            CouponDTO dto = new CouponDTO();
            BeanUtils.copyProperties(coupon, dto);
            return dto;
        }).collect(Collectors.toList());
        return R.ok(PageResult.of(dtoList, page.getTotal(), pageNum, pageSize));
    }

    @Override
    @PostMapping("/coupon")
    public R<Long> createCoupon(@RequestBody CouponDTO couponDTO) {
        Coupon coupon = new Coupon();
        BeanUtils.copyProperties(couponDTO, coupon);
        couponMapper.insert(coupon);
        return R.ok(coupon.getId());
    }

    @Override
    @PutMapping("/coupon/{couponId}")
    public R<Boolean> updateCoupon(@PathVariable("couponId") Long couponId, @RequestBody CouponDTO couponDTO) {
        Coupon coupon = couponMapper.selectById(couponId);
        if (coupon == null) return R.fail("优惠券不存在");
        BeanUtils.copyProperties(couponDTO, coupon);
        coupon.setId(couponId);
        return R.ok(couponMapper.updateById(coupon) > 0);
    }

    @Override
    @DeleteMapping("/coupon/{couponId}")
    public R<Boolean> deleteCoupon(@PathVariable("couponId") Long couponId) {
        return R.ok(couponMapper.deleteById(couponId) > 0);
    }

    @Override
    @GetMapping("/seckill/list")
    public R<List<SeckillActivityDTO>> listSeckillActivities() {
        List<SeckillActivity> activities = seckillService.getSeckillList();
        List<SeckillActivityDTO> dtoList = activities.stream().map(activity -> {
            SeckillActivityDTO dto = new SeckillActivityDTO();
            BeanUtils.copyProperties(activity, dto);
            return dto;
        }).collect(Collectors.toList());
        return R.ok(dtoList);
    }

    @Override
    @GetMapping("/seckill/{activityId}")
    public R<SeckillActivityDTO> getSeckillActivityById(@PathVariable("activityId") Long activityId) {
        SeckillActivity activity = seckillActivityMapper.selectById(activityId);
        if (activity == null) return R.fail("秒杀活动不存在");
        SeckillActivityDTO dto = new SeckillActivityDTO();
        BeanUtils.copyProperties(activity, dto);
        return R.ok(dto);
    }

    @Override
    @PostMapping("/seckill")
    public R<Long> createSeckillActivity(@RequestBody SeckillActivityDTO activityDTO) {
        SeckillActivity activity = new SeckillActivity();
        BeanUtils.copyProperties(activityDTO, activity);
        seckillService.createActivity(activity);
        return R.ok(activity.getId());
    }

    @Override
    @PutMapping("/seckill/{activityId}")
    public R<Boolean> updateSeckillActivity(@PathVariable("activityId") Long activityId, @RequestBody SeckillActivityDTO activityDTO) {
        SeckillActivity activity = seckillActivityMapper.selectById(activityId);
        if (activity == null) return R.fail("秒杀活动不存在");
        BeanUtils.copyProperties(activityDTO, activity);
        activity.setId(activityId);
        return R.ok(seckillActivityMapper.updateById(activity) > 0);
    }

    @Override
    @DeleteMapping("/seckill/{activityId}")
    public R<Boolean> deleteSeckillActivity(@PathVariable("activityId") Long activityId) {
        return R.ok(seckillActivityMapper.deleteById(activityId) > 0);
    }
}
