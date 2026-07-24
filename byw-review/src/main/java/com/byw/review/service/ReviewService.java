package com.byw.review.service;

import com.byw.api.review.dto.ReviewStatsDTO;
import com.byw.common.core.result.PageResult;
import com.byw.review.document.ReviewDetail;
import com.byw.review.entity.Review;

import java.util.Map;

public interface ReviewService {

    /**
     * 创建评价
     */
    void createReview(ReviewDetail reviewDetail, Integer rating, String content, Integer isAnonymous);

    /**
     * 追加评价
     */
    void appendReview(String orderNo, Long userId, Long skuId, String appendContent, java.util.List<String> appendImages);

    /**
     * 获取商品评价列表
     */
    PageResult<ReviewDetail> getReviewsByProduct(Long productId, Integer rating, Boolean hasImage,
                                                  Integer pageNum, Integer pageSize);

    /**
     * 获取商品评价统计
     */
    ReviewStatsDTO getReviewStats(Long productId);

    /**
     * 检查订单是否已评价
     */
    boolean reviewExists(String orderNo);

    /**
     * 管理端：获取评价列表（含真实商品名称、图片与追评信息）
     */
    PageResult<Map<String, Object>> adminListReviews(Integer pageNum, Integer pageSize, Integer rating, Integer status);

    /**
     * 管理端：更新评价显示状态
     */
    void adminUpdateVisible(Long reviewId, Integer status);

    /**
     * 管理端：删除评价
     */
    void adminDeleteReview(Long reviewId);

    /**
     * 批量创建评价（一个订单多个商品）
     */
    void createBatchReviews(Long userId, String orderNo, java.util.List<ReviewDetail> reviewDetails);

    /**
     * 获取用户评价列表（含图片与追评信息）
     */
    PageResult<Map<String, Object>> getUserReviews(Long userId, Integer pageNum, Integer pageSize, Boolean hasImage);

    /**
     * 获取某订单本人的评价明细（含追评），用于订单卡片摘要
     */
    java.util.List<ReviewDetail> getOrderReviews(String orderNo, Long userId);
}
