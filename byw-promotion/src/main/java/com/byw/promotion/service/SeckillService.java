package com.byw.promotion.service;

import com.byw.promotion.entity.SeckillActivity;
import com.byw.promotion.entity.SeckillActivityItem;

import java.util.List;

public interface SeckillService {

    /** 每个活动最多可配置的商品数 */
    int MAX_ITEMS_PER_ACTIVITY = 50;

    /**
     * 创建秒杀活动（含商品条目）
     */
    void createActivity(SeckillActivity activity, List<SeckillActivityItem> items);

    /**
     * 更新秒杀活动（全量替换商品条目）
     */
    void updateActivity(SeckillActivity activity, List<SeckillActivityItem> items);

    /**
     * 删除秒杀活动（级联删除商品条目）
     */
    void deleteActivity(Long activityId);

    /**
     * 秒杀（按活动商品条目抢购）
     *
     * @param addressId 收货地址ID，为空时回退使用用户默认地址
     */
    void seckill(Long activityId, Long itemId, Long userId, Long addressId);

    /**
     * 获取秒杀活动列表（用户端，仅未结束）
     */
    List<SeckillActivity> getSeckillList();

    /**
     * 获取所有秒杀活动（管理端，全状态）
     */
    List<SeckillActivity> getAllSeckillList();

    /**
     * 获取活动的商品条目列表
     */
    List<SeckillActivityItem> getItemsByActivityId(Long activityId);
}
