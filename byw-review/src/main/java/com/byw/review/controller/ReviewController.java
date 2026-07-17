package com.byw.review.controller;

import com.byw.api.review.dto.ReviewStatsDTO;
import com.byw.common.core.result.PageResult;
import com.byw.common.core.result.R;
import com.byw.common.security.annotation.RequireLogin;
import com.byw.common.security.context.UserContext;
import com.byw.review.document.ReviewDetail;
import com.byw.review.entity.Review;
import com.byw.review.service.ReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "评价", description = "评价管理")
@RestController
@RequestMapping("/review")
@RequiredArgsConstructor
@RequireLogin
public class ReviewController {

    private final ReviewService reviewService;

    @Operation(summary = "创建评价")
    @PostMapping("/create")
    public R<Void> create(@RequestBody CreateReviewRequest request) {
        ReviewDetail detail = new ReviewDetail();
        detail.setOrderId(request.getOrderNo());
        detail.setProductId(request.getProductId());
        detail.setUserId(UserContext.getUserId());
        detail.setRating(request.getRating());
        detail.setContent(request.getContent());
        detail.setImages(request.getImages());
        detail.setVideos(request.getVideos());

        reviewService.createReview(detail, request.getRating(), request.getContent(), request.getIsAnonymous());
        return R.ok();
    }

    @Operation(summary = "追加评价")
    @PostMapping("/append")
    public R<Void> append(@RequestBody AppendReviewRequest request) {
        reviewService.appendReview(request.getOrderNo(), UserContext.getUserId(),
                request.getAppendContent(), request.getAppendImages());
        return R.ok();
    }

    @Operation(summary = "获取商品评价列表")
    @GetMapping("/product/{productId}")
    public R<PageResult<ReviewDetail>> getByProduct(
            @PathVariable Long productId,
            @RequestParam(required = false) Integer rating,
            @RequestParam(required = false) Boolean hasImage,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        return R.ok(reviewService.getReviewsByProduct(productId, rating, hasImage, pageNum, pageSize));
    }

    @Operation(summary = "获取商品评价统计")
    @GetMapping("/stats/{productId}")
    public R<ReviewStatsDTO> getStats(@PathVariable Long productId) {
        return R.ok(reviewService.getReviewStats(productId));
    }

    @Operation(summary = "批量创建评价")
    @PostMapping("/create-batch")
    public R<Void> createBatch(@RequestBody BatchReviewRequest request) {
        List<ReviewDetail> details = new java.util.ArrayList<>();
        for (ReviewItem item : request.getReviews()) {
            ReviewDetail detail = new ReviewDetail();
            detail.setOrderId(item.getOrderNo());
            detail.setProductId(item.getProductId());
            detail.setSkuId(item.getSkuId());
            detail.setUserId(UserContext.getUserId());
            detail.setRating(item.getRating());
            detail.setContent(item.getContent());
            detail.setImages(item.getImages());
            details.add(detail);
        }
        String orderNo = request.getReviews().get(0).getOrderNo();
        reviewService.createBatchReviews(UserContext.getUserId(), orderNo, details);
        return R.ok();
    }

    @Operation(summary = "获取我的评价列表")
    @GetMapping("/my-reviews")
    public R<PageResult<Review>> myReviews(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) Boolean hasImage) {
        return R.ok(reviewService.getUserReviews(UserContext.getUserId(), pageNum, pageSize, hasImage));
    }

    @Data
    public static class CreateReviewRequest {
        private String orderNo;
        private Long productId;
        private Integer rating;
        private String content;
        private List<String> images;
        private List<String> videos;
        private Integer isAnonymous;
    }

    @Data
    public static class BatchReviewRequest {
        private List<ReviewItem> reviews;
    }

    @Data
    public static class ReviewItem {
        private String orderNo;
        private Long productId;
        private Long skuId;
        private Integer rating;
        private String content;
        private List<String> images;
        private Integer isAnonymous;
    }

    @Data
    public static class AppendReviewRequest {
        private String orderNo;
        private String appendContent;
        private List<String> appendImages;
    }
}
