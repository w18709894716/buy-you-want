package com.byw.promotion.service;

import com.byw.api.promotion.dto.CouponDTO;
import com.byw.api.promotion.dto.UserCouponDTO;
import com.byw.promotion.entity.Coupon;

import java.math.BigDecimal;
import java.util.List;

public interface CouponService {

    /**
     * 获取可用优惠券列表
     * @param newUser 新人筛选：null=全部，0=仅普通券，1=仅新人专享券
     */
    List<Coupon> listAvailable(Integer newUser);

    /**
     * 获取指定店铺可领取的店铺券（有余量、在有效期、且当前用户未领过）
     * @param shopId 店铺ID
     * @param userId 当前用户ID，用于排除已领取的券
     */
    List<Coupon> listShopClaimable(Long shopId, Long userId);

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

    /**
     * 获取用户持有的可用优惠券
     */
    List<UserCouponDTO> listUserCoupons(Long userId, Integer status);
}
