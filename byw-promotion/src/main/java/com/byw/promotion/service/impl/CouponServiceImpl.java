package com.byw.promotion.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.byw.api.promotion.dto.CouponDTO;
import com.byw.common.core.exception.BusinessException;
import com.byw.common.redis.util.RedisUtil;
import com.byw.promotion.entity.Coupon;
import com.byw.promotion.entity.CouponRecord;
import com.byw.promotion.mapper.CouponMapper;
import com.byw.promotion.mapper.CouponRecordMapper;
import com.byw.promotion.service.CouponService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CouponServiceImpl implements CouponService {

    private final CouponMapper couponMapper;
    private final CouponRecordMapper couponRecordMapper;
    private final RedisUtil redisUtil;

    private static final String COUPON_CLAIM_KEY = "coupon:claim:";

    @Override
    public List<Coupon> listAvailable() {
        return couponMapper.selectList(
                new LambdaQueryWrapper<Coupon>()
                        .eq(Coupon::getStatus, 1)
                        .le(Coupon::getStartTime, LocalDateTime.now())
                        .ge(Coupon::getEndTime, LocalDateTime.now())
                        .apply("claimed_count < total_count"));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void claimCoupon(Long userId, Long couponId) {
        // 1. 查询优惠券
        Coupon coupon = couponMapper.selectById(couponId);
        if (coupon == null || coupon.getStatus() != 1) {
            throw new BusinessException("优惠券不存在或已下线");
        }

        // 2. 检查是否在有效期内
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(coupon.getStartTime()) || now.isAfter(coupon.getEndTime())) {
            throw new BusinessException("优惠券不在有效期内");
        }

        // 3. 使用Redis increment防止超领
        String redisKey = COUPON_CLAIM_KEY + couponId;
        Long claimedCount = redisUtil.increment(redisKey);
        if (claimedCount > coupon.getTotalCount()) {
            // 回退计数
            redisUtil.increment(redisKey, -1);
            throw new BusinessException("优惠券已领完");
        }

        // 4. 检查用户是否已领取（防止重复领取）
        long userClaimCount = couponRecordMapper.selectCount(
                new LambdaQueryWrapper<CouponRecord>()
                        .eq(CouponRecord::getCouponId, couponId)
                        .eq(CouponRecord::getUserId, userId));
        if (userClaimCount > 0) {
            redisUtil.increment(redisKey, -1);
            throw new BusinessException("已领取过该优惠券");
        }

        // 5. 创建领取记录
        CouponRecord record = new CouponRecord();
        record.setCouponId(couponId);
        record.setUserId(userId);
        record.setStatus(0); // 未使用
        couponRecordMapper.insert(record);

        // 6. 更新已领取数量
        coupon.setClaimedCount(coupon.getClaimedCount() + 1);
        couponMapper.updateById(coupon);

        log.info("用户领取优惠券: userId={}, couponId={}", userId, couponId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BigDecimal useCoupon(Long couponId, Long userId, BigDecimal orderAmount) {
        // 1. 查询优惠券
        Coupon coupon = couponMapper.selectById(couponId);
        if (coupon == null) {
            throw new BusinessException("优惠券不存在");
        }

        // 2. 查询用户的优惠券记录
        CouponRecord record = couponRecordMapper.selectOne(
                new LambdaQueryWrapper<CouponRecord>()
                        .eq(CouponRecord::getCouponId, couponId)
                        .eq(CouponRecord::getUserId, userId)
                        .eq(CouponRecord::getStatus, 0));
        if (record == null) {
            throw new BusinessException("未持有该优惠券或已使用");
        }

        // 3. 计算优惠金额
        BigDecimal discount = calculateDiscount(coupon, orderAmount);

        // 4. 更新使用状态
        record.setStatus(1); // 已使用
        record.setUsedTime(LocalDateTime.now());
        couponRecordMapper.updateById(record);

        log.info("使用优惠券: couponId={}, userId={}, discount={}", couponId, userId, discount);
        return discount;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void releaseCoupon(Long couponId, Long userId) {
        // 查找已使用的记录并恢复
        CouponRecord record = couponRecordMapper.selectOne(
                new LambdaQueryWrapper<CouponRecord>()
                        .eq(CouponRecord::getCouponId, couponId)
                        .eq(CouponRecord::getUserId, userId)
                        .eq(CouponRecord::getStatus, 1));
        if (record != null) {
            record.setStatus(0); // 恢复为未使用
            record.setUsedTime(null);
            couponRecordMapper.updateById(record);
            log.info("释放优惠券: couponId={}, userId={}", couponId, userId);
        }
    }

    @Override
    public CouponDTO getCouponById(Long couponId) {
        Coupon coupon = couponMapper.selectById(couponId);
        if (coupon == null) {
            return null;
        }
        CouponDTO dto = new CouponDTO();
        BeanUtils.copyProperties(coupon, dto);
        return dto;
    }

    // ==================== 私有方法 ====================

    /**
     * 根据优惠券类型计算优惠金额
     * type 1: 满减券 - 满足最低金额后减去固定金额
     * type 2: 折扣券 - 按比例折扣
     * type 3: 无门槛券 - 直接减去固定金额
     */
    private BigDecimal calculateDiscount(Coupon coupon, BigDecimal orderAmount) {
        BigDecimal discountValue = coupon.getDiscountValue();
        BigDecimal minAmount = coupon.getMinAmount();

        switch (coupon.getType()) {
            case 1: // 满减券
                if (orderAmount.compareTo(minAmount) < 0) {
                    throw new BusinessException("订单金额未达到满减门槛: " + minAmount + "元");
                }
                return discountValue.min(orderAmount);

            case 2: // 折扣券
                if (orderAmount.compareTo(minAmount) < 0) {
                    throw new BusinessException("订单金额未达到折扣门槛: " + minAmount + "元");
                }
                // discountValue表示折扣比例, 如0.85表示85折
                BigDecimal discountedAmount = orderAmount.multiply(discountValue).setScale(2, RoundingMode.HALF_UP);
                return orderAmount.subtract(discountedAmount);

            case 3: // 无门槛券
                return discountValue.min(orderAmount);

            default:
                throw new BusinessException("未知的优惠券类型");
        }
    }
}
