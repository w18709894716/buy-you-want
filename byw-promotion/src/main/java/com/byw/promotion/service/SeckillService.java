package com.byw.promotion.service;

import com.byw.promotion.entity.SeckillActivity;

import java.util.List;

public interface SeckillService {

    /**
     * 创建秒杀活动
     */
    void createActivity(SeckillActivity activity);

    /**
     * 秒杀
     */
    void seckill(Long activityId, Long userId);

    /**
     * 获取秒杀活动列表
     */
    List<SeckillActivity> getSeckillList();
}
