package com.byw.promotion.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.byw.common.core.exception.BusinessException;
import com.byw.common.redis.util.RedisUtil;
import com.byw.promotion.entity.GroupBuyActivity;
import com.byw.promotion.mapper.GroupBuyActivityMapper;
import com.byw.promotion.service.GroupBuyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class GroupBuyServiceImpl implements GroupBuyService {

    private final GroupBuyActivityMapper groupBuyActivityMapper;
    private final RedisUtil redisUtil;

    /** 参团人数计数 key（沿用原有 key 名，保持与已有 Redis 数据兼容） */
    private static final String GROUP_BUY_KEY = "groupbuy:activity:";

    /** 已参团用户集合 key：用于"同一用户只参团一次" */
    private static final String GROUP_BUY_JOINED_KEY = "groupbuy:joined:";

    @Override
    public List<GroupBuyActivity> listActivities() {
        return groupBuyActivityMapper.selectList(
                new LambdaQueryWrapper<GroupBuyActivity>()
                        .eq(GroupBuyActivity::getStatus, 1)
                        .le(GroupBuyActivity::getStartTime, LocalDateTime.now())
                        .ge(GroupBuyActivity::getEndTime, LocalDateTime.now()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void joinActivity(Long activityId, Long userId) {
        // 1. 查询活动
        GroupBuyActivity activity = groupBuyActivityMapper.selectById(activityId);
        if (activity == null || activity.getStatus() != 1) {
            throw new BusinessException("拼团活动不存在或未开始");
        }

        // 2. 检查时间
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(activity.getStartTime()) || now.isAfter(activity.getEndTime())) {
            throw new BusinessException("拼团活动不在有效期内");
        }

        // 3. 按用户去重：sAdd 是原子操作，返回 0 表示该用户已在集合中。
        //    原先没有任何按用户去重，同一个用户可以无限次参团、把计数刷满，直接占掉活动名额。
        String joinedKey = GROUP_BUY_JOINED_KEY + activityId;
        Long added = redisUtil.sAdd(joinedKey, userId);
        if (added == null || added == 0) {
            log.info("用户重复参团，忽略本次请求: activityId={}, userId={}", activityId, userId);
            return;
        }

        // 4. 计数并校验名额
        String redisKey = GROUP_BUY_KEY + activityId;
        Long joinCount = redisUtil.increment(redisKey);
        if (activity.getMaxGroups() != null && joinCount != null
                && joinCount > activity.getMaxGroups() * activity.getGroupSize()) {
            // 名额已满：回滚本次占用（计数 -1 + 移出已参团集合），避免两个 key 不一致
            redisUtil.increment(redisKey, -1);
            redisUtil.sRemove(joinedKey, userId);
            throw new BusinessException("拼团人数已满");
        }

        // 5. 两个 key 都设置与活动一致的过期时间，避免活动结束后长期占用 Redis（原先计数 key 永不过期）
        long ttlSeconds = Math.max(60L, Duration.between(now, activity.getEndTime()).getSeconds() + 60L);
        redisUtil.expire(joinedKey, ttlSeconds, TimeUnit.SECONDS);
        redisUtil.expire(redisKey, ttlSeconds, TimeUnit.SECONDS);

        log.info("用户参加拼团: activityId={}, userId={}, joinCount={}", activityId, userId, joinCount);
    }
}
