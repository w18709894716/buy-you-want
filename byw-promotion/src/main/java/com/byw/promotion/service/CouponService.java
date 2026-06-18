package com.byw.promotion.service;

import com.byw.api.promotion.dto.CouponDTO;
import com.byw.promotion.entity.Coupon;

import java.math.BigDecimal;
import java.util.List;

public interface CouponService {

    /**
     * 获取可用优惠券列表
     */
    List<Coupon> listAvailable();

    /**
     * 领取优惠券
     */
    void claimCoupon(Long userId, Long couponId);

    /**
     * 使用优惠券
     * @return 优惠金额
     */
    BigDecimal useCoupon(Long couponId, Long userId, BigDecimal orderAmount);

    /**
     * 释放优惠券（取消订单时）
     */
    void releaseCoupon(Long couponId, Long userId);

    /**
     * 根据ID获取优惠券
     */
    CouponDTO getCouponById(Long couponId);
}
