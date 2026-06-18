package com.byw.promotion.service;

import com.byw.promotion.entity.GroupBuyActivity;

import java.util.List;

public interface GroupBuyService {

    /**
     * 获取拼团活动列表
     */
    List<GroupBuyActivity> listActivities();

    /**
     * 参加拼团
     */
    void joinActivity(Long activityId, Long userId);
}
