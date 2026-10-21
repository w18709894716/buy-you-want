package com.byw.promotion.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.byw.api.order.OrderFeignClient;
import com.byw.api.promotion.dto.CouponDTO;
import com.byw.api.promotion.dto.UserCouponDTO;
import com.byw.api.shop.ShopFeignClient;
import com.byw.api.shop.dto.ShopDTO;
import com.byw.common.core.exception.BusinessException;
import com.byw.common.core.result.R;
import com.byw.promotion.entity.Coupon;
import com.byw.promotion.entity.CouponRecord;
import com.byw.promotion.mapper.CouponMapper;
import com.byw.promotion.mapper.CouponRecordMapper;
import com.byw.promotion.service.CouponService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.dao.DuplicateKeyException;
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
    private final OrderFeignClient orderFeignClient;
    private final ShopFeignClient shopFeignClient;

    @Override
    public List<Coupon> listAvailable(Integer newUser) {
        return couponMapper.selectList(
                new LambdaQueryWrapper<Coupon>()
                        .eq(Coupon::getStatus, 1)
                        .le(Coupon::getStartTime, LocalDateTime.now())
                        .ge(Coupon::getEndTime, LocalDateTime.now())
                        // newUser=1 只取新人专享；newUser=0 只取普通券（含旧数据 null）；null 不限
                        .eq(newUser != null && newUser == 1, Coupon::getNewUser, 1)
                        .and(newUser != null && newUser == 0,
                                w -> w.ne(Coupon::getNewUser, 1).or().isNull(Coupon::getNewUser))
                        // 领券中心只展示平台券，店铺券在对应店铺商品详情页弹框领取
                        .and(w -> w.isNull(Coupon::getShopId).or().eq(Coupon::getShopId, 0L))
                        .apply("IFNULL(claimed_count, 0) < total_count"));
    }

    @Override
    public List<Coupon> listShopClaimable(Long shopId, Long userId) {
        if (shopId == null || shopId == 0) {
            return List.of();
        }
        List<Coupon> coupons = couponMapper.selectList(
                new LambdaQueryWrapper<Coupon>()
                        .eq(Coupon::getStatus, 1)
                        .eq(Coupon::getShopId, shopId)
                        .le(Coupon::getStartTime, LocalDateTime.now())
                        .ge(Coupon::getEndTime, LocalDateTime.now())
                        .apply("IFNULL(claimed_count, 0) < total_count"));
        if (coupons.isEmpty() || userId == null) {
            return coupons;
        }
        // 过滤掉当前用户已领取的券，避免重复弹框提示
        java.util.Set<Long> claimedIds = couponRecordMapper.selectList(
                        new LambdaQueryWrapper<CouponRecord>()
                                .eq(CouponRecord::getUserId, userId)
                                .in(CouponRecord::getCouponId,
                                        coupons.stream().map(Coupon::getId).collect(java.util.stream.Collectors.toList())))
                .stream().map(CouponRecord::getCouponId).collect(java.util.stream.Collectors.toSet());
        return coupons.stream()
                .filter(c -> !claimedIds.contains(c.getId()))
                .collect(java.util.stream.Collectors.toList());
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

        // 2.1 新人专享券：仅限新用户（无历史订单）领取
        if (coupon.getNewUser() != null && coupon.getNewUser() == 1) {
            long orderCount;
            try {
                Long c = orderFeignClient.countUserOrders(userId).getData();
                orderCount = c == null ? 0L : c;
            } catch (Exception e) {
                log.warn("校验新用户领取资格失败: userId={}, err={}", userId, e.getMessage());
                throw new BusinessException("暂时无法校验领取资格，请稍后再试");
            }
            if (orderCount > 0) {
                throw new BusinessException("该优惠券仅限新用户领取");
            }
        }

        // 3. 检查用户是否已领取（仅为友好提示；并发兜底由唯一键 uk_coupon_user 完成）
        long userClaimCount = couponRecordMapper.selectCount(
                new LambdaQueryWrapper<CouponRecord>()
                        .eq(CouponRecord::getCouponId, couponId)
                        .eq(CouponRecord::getUserId, userId));
        if (userClaimCount > 0) {
            throw new BusinessException("已领取过该优惠券");
        }

        // 4. 原子占用额度：以数据库为唯一真相源。
        //    原先用 Redis 计数当闸门，但它不从 claimed_count 初始化、也没有 TTL：
        //    Redis 重启即归零（可超发），且与数据库计数永久漂移、无对账。
        //    改成带条件的 UPDATE 后，自增与领券记录处在同一个事务里，
        //    后续任何一步失败都会连同这次自增一起回滚 —— 既不会超发也不会漂移。
        int claimed = couponMapper.update(null, new LambdaUpdateWrapper<Coupon>()
                .eq(Coupon::getId, couponId)
                .apply("IFNULL(claimed_count, 0) < total_count")
                .setSql("claimed_count = IFNULL(claimed_count, 0) + 1"));
        if (claimed == 0) {
            throw new BusinessException("优惠券已领完");
        }

        // 5. 创建领取记录：并发下"先查再插"挡不住重复（两个请求可能同时通过第 3 步），
        //    由唯一键 uk_coupon_user(coupon_id, user_id) 兜底
        CouponRecord record = new CouponRecord();
        record.setCouponId(couponId);
        record.setUserId(userId);
        record.setStatus(0); // 未使用
        try {
            couponRecordMapper.insert(record);
        } catch (DuplicateKeyException e) {
            throw new BusinessException("已领取过该优惠券");
        }

        log.info("用户领取优惠券: userId={}, couponId={}", userId, couponId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BigDecimal useCoupon(Long couponId, Long userId, BigDecimal orderAmount) {
        // 1. 校验券本身：存在、未下线、在有效期内
        //    原先只在"领取"时校验有效期，导致过期券、已下线的券依然可以抵扣（直接调接口即可绕过前端）
        Coupon coupon = couponMapper.selectById(couponId);
        if (coupon == null) {
            throw new BusinessException("优惠券不存在");
        }
        if (coupon.getStatus() == null || coupon.getStatus() != 1) {
            throw new BusinessException("优惠券已下线");
        }
        LocalDateTime now = LocalDateTime.now();
        if ((coupon.getStartTime() != null && now.isBefore(coupon.getStartTime()))
                || (coupon.getEndTime() != null && now.isAfter(coupon.getEndTime()))) {
            throw new BusinessException("优惠券已过期");
        }

        // 2. 原子核销：只有仍然"未使用"的记录能被翻成"已使用"。
        //    原先"先查再改"在并发/重放下会让同一张券被核销两次（重复折扣），
        //    改为条件更新后，后者影响 0 行即判定失败。
        int used = couponRecordMapper.update(null, new LambdaUpdateWrapper<CouponRecord>()
                .eq(CouponRecord::getCouponId, couponId)
                .eq(CouponRecord::getUserId, userId)
                .eq(CouponRecord::getStatus, 0)
                .set(CouponRecord::getStatus, 1)
                .set(CouponRecord::getUsedTime, now));
        if (used == 0) {
            // 区分文案：是为了友好提示才多查一次，只在失败路径上执行
            long owned = couponRecordMapper.selectCount(new LambdaQueryWrapper<CouponRecord>()
                    .eq(CouponRecord::getCouponId, couponId)
                    .eq(CouponRecord::getUserId, userId));
            throw new BusinessException(owned > 0 ? "优惠券已使用" : "未持有该优惠券");
        }

        // 3. 计算优惠金额。放在核销之后：若门槛不足等校验失败，异常会让整个事务回滚，
        //    券状态不会残留为"已使用"。未达门槛的错误文案更具体，这里保留原始异常。
        BigDecimal discount = calculateDiscount(coupon, orderAmount);

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

    @Override
    public List<UserCouponDTO> listUserCoupons(Long userId, Integer status) {
        // 查询用户的优惠券领取记录
        List<CouponRecord> records = couponRecordMapper.selectList(
                new LambdaQueryWrapper<CouponRecord>()
                        .eq(CouponRecord::getUserId, userId)
                        .eq(CouponRecord::getStatus, status)
                        .orderByDesc(CouponRecord::getCreatedAt));
        if (records.isEmpty()) {
            return List.of();
        }
        // 查询优惠券模板详情：一次性批量查（原先在循环里逐个 selectById，是典型的 N+1）
        List<Long> couponIds = records.stream()
                .map(CouponRecord::getCouponId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        java.util.Map<Long, Coupon> couponMap = couponIds.isEmpty()
                ? java.util.Map.of()
                : couponMapper.selectBatchIds(couponIds).stream()
                        .collect(java.util.stream.Collectors.toMap(Coupon::getId, c -> c, (a, b) -> a));
        List<UserCouponDTO> result = new java.util.ArrayList<>();
        // 同批次相同店铺仅查一次名称
        java.util.Map<Long, String> shopNameCache = new java.util.HashMap<>();
        for (CouponRecord record : records) {
            Coupon coupon = couponMap.get(record.getCouponId());
            if (coupon == null) continue;
            UserCouponDTO dto = new UserCouponDTO();
            dto.setRecordId(record.getId());
            dto.setCouponId(coupon.getId());
            dto.setName(coupon.getName());
            dto.setType(coupon.getType());
            dto.setDiscountValue(coupon.getDiscountValue());
            dto.setMinAmount(coupon.getMinAmount());
            dto.setStartTime(coupon.getStartTime());
            dto.setEndTime(coupon.getEndTime());
            // 店铺券透出归属店铺，供前端按店限制使用范围
            if (coupon.getShopId() != null && coupon.getShopId() != 0) {
                dto.setShopId(coupon.getShopId());
                dto.setShopName(resolveShopName(coupon.getShopId(), shopNameCache));
            }
            result.add(dto);
        }
        return result;
    }

    /**
     * 查询店铺名称，结果缓存避免重复远程调用；失败时返回 null 不阻断列表
     */
    private String resolveShopName(Long shopId, java.util.Map<Long, String> cache) {
        return cache.computeIfAbsent(shopId, sid -> {
            try {
                R<ShopDTO> resp = shopFeignClient.getShopById(sid);
                if (resp != null && resp.isSuccess() && resp.getData() != null) {
                    return resp.getData().getName();
                }
            } catch (Exception e) {
                log.warn("获取店铺名称失败: shopId={}, error={}", sid, e.getMessage());
            }
            return null;
        });
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
        // min_amount 可空（建表默认 0.00），直接比较会 NPE
        BigDecimal minAmount = coupon.getMinAmount() == null ? BigDecimal.ZERO : coupon.getMinAmount();
        if (coupon.getType() == null || discountValue == null) {
            throw new BusinessException("优惠券配置异常，请联系客服");
        }

        switch (coupon.getType()) {
            case 1: // 满减券
                if (orderAmount.compareTo(minAmount) < 0) {
                    throw new BusinessException("订单金额未达到满减门槛: " + minAmount + "元");
                }
                return discountValue.min(orderAmount).max(BigDecimal.ZERO);

            case 2: // 折扣券
                if (orderAmount.compareTo(minAmount) < 0) {
                    throw new BusinessException("订单金额未达到折扣门槛: " + minAmount + "元");
                }
                // discountValue表示折扣比例, 如0.85表示85折
                BigDecimal discountedAmount = orderAmount.multiply(discountValue).setScale(2, RoundingMode.HALF_UP);
                // 兜底：折扣比例被配成 >1 时差额为负，若不夹住会让"优惠"变成反向加价
                return orderAmount.subtract(discountedAmount).min(orderAmount).max(BigDecimal.ZERO);

            case 3: // 无门槛券
                return discountValue.min(orderAmount).max(BigDecimal.ZERO);

            default:
                throw new BusinessException("未知的优惠券类型");
        }
    }
}
