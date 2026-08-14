package com.byw.user.service;

import java.util.List;

public interface UserShopFollowService {

    /** 关注（幂等） */
    void follow(Long userId, Long shopId);

    /** 取消关注 */
    void unfollow(Long userId, Long shopId);

    /** 是否已关注 */
    boolean isFollowing(Long userId, Long shopId);

    /** 店铺粉丝数 */
    long followerCount(Long shopId);

    /** 已关注的店铺ID列表（按关注时间倒序） */
    List<Long> followedShopIds(Long userId);
}
