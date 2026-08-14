package com.byw.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.byw.user.entity.UserShopFollow;
import com.byw.user.mapper.UserShopFollowMapper;
import com.byw.user.service.UserShopFollowService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserShopFollowServiceImpl implements UserShopFollowService {

    private final UserShopFollowMapper userShopFollowMapper;

    @Override
    public void follow(Long userId, Long shopId) {
        UserShopFollow exist = userShopFollowMapper.selectByUserAndShopIncludeDeleted(userId, shopId);
        if (exist != null && (exist.getDeleted() == null || exist.getDeleted() == 0)) {
            // 已关注，幂等返回
            return;
        }
        if (exist != null) {
            // 取消后重新关注：恢复软删除记录
            userShopFollowMapper.restoreById(exist.getId());
            return;
        }
        UserShopFollow follow = new UserShopFollow();
        follow.setUserId(userId);
        follow.setShopId(shopId);
        userShopFollowMapper.insert(follow);
    }

    @Override
    public void unfollow(Long userId, Long shopId) {
        userShopFollowMapper.delete(new LambdaQueryWrapper<UserShopFollow>()
                .eq(UserShopFollow::getUserId, userId)
                .eq(UserShopFollow::getShopId, shopId));
    }

    @Override
    public boolean isFollowing(Long userId, Long shopId) {
        return userShopFollowMapper.selectCount(new LambdaQueryWrapper<UserShopFollow>()
                .eq(UserShopFollow::getUserId, userId)
                .eq(UserShopFollow::getShopId, shopId)) > 0;
    }

    @Override
    public long followerCount(Long shopId) {
        return userShopFollowMapper.selectCount(new LambdaQueryWrapper<UserShopFollow>()
                .eq(UserShopFollow::getShopId, shopId));
    }

    @Override
    public List<Long> followedShopIds(Long userId) {
        return userShopFollowMapper.selectList(new LambdaQueryWrapper<UserShopFollow>()
                .eq(UserShopFollow::getUserId, userId)
                .orderByDesc(UserShopFollow::getCreatedAt))
                .stream()
                .map(UserShopFollow::getShopId)
                .collect(Collectors.toList());
    }
}
