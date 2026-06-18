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

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class GroupBuyServiceImpl implements GroupBuyService {

    private final GroupBuyActivityMapper groupBuyActivityMapper;
    private final RedisUtil redisUtil;

    private static final String GROUP_BUY_KEY = "groupbuy:activity:";

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

        // 3. 使用Redis记录参团用户
        String redisKey = GROUP_BUY_KEY + activityId;
        Long joinCount = redisUtil.increment(redisKey);

        // 4. 检查是否超过最大团数
        if (activity.getMaxGroups() != null && joinCount > activity.getMaxGroups() * activity.getGroupSize()) {
            redisUtil.increment(redisKey, -1);
            throw new BusinessException("拼团人数已满");
        }

        log.info("用户参加拼团: activityId={}, userId={}, joinCount={}", activityId, userId, joinCount);
    }
}
