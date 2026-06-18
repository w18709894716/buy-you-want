package com.byw.review.service;

import com.byw.api.review.dto.ReviewStatsDTO;
import com.byw.common.core.result.PageResult;
import com.byw.review.document.ReviewDetail;

public interface ReviewService {

    /**
     * 创建评价
     */
    void createReview(ReviewDetail reviewDetail, Integer rating, String content, Integer isAnonymous);

    /**
     * 追加评价
     */
    void appendReview(String orderNo, Long userId, String appendContent, java.util.List<String> appendImages);

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
}
