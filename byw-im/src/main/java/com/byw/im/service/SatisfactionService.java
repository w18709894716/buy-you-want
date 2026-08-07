package com.byw.im.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.byw.im.entity.Satisfaction;

/**
 * IM 满意度评价服务
 */
public interface SatisfactionService {

    /**
     * 提交评价（幂等：conversationId 唯一约束）
     * @return 评价记录
     */
    Satisfaction submit(Satisfaction satisfaction);

    /**
     * 检查会话是否已评价（C端展示评价入口时）
     */
    boolean hasRated(Long conversationId, Long userId);

    /**
     * 商家分页查询本店评价
     */
    IPage<Satisfaction> listByShop(Long shopId, Integer page, Integer pageSize);

    /**
     * 商家评分统计
     */
    SatisfactionStats stats(Long shopId);

    /** 评分统计 DTO */
    record SatisfactionStats(
            double avgRating,
            long totalCount,
            long rating5Count,
            long rating4Count,
            long rating3Count,
            long rating2Count,
            long rating1Count
    ) {}
}