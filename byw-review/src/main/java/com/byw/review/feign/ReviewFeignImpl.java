package com.byw.review.feign;

import com.byw.api.review.ReviewFeignClient;
import com.byw.api.review.dto.ReviewStatsDTO;
import com.byw.common.core.result.PageResult;
import com.byw.common.core.result.R;
import com.byw.review.service.ReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/feign/review")
@RequiredArgsConstructor
public class ReviewFeignImpl implements ReviewFeignClient {

    private final ReviewService reviewService;

    @Override
    @GetMapping("/stats/{productId}")
    public R<ReviewStatsDTO> getReviewStats(@PathVariable("productId") Long productId) {
        return R.ok(reviewService.getReviewStats(productId));
    }

    @Override
    @GetMapping("/exists/{orderNo}")
    public R<Boolean> reviewExists(@PathVariable("orderNo") String orderNo) {
        return R.ok(reviewService.reviewExists(orderNo));
    }

    @Override
    @GetMapping("/admin/list")
    public R<PageResult<Map<String, Object>>> adminListReviews(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) Integer rating,
            @RequestParam(required = false) Integer status) {
        return R.ok(reviewService.adminListReviews(pageNum, pageSize, rating, status));
    }

    @Override
    @PutMapping("/admin/{id}/visible")
    public R<Void> adminUpdateVisible(@PathVariable("id") Long id, @RequestParam("visible") Boolean visible) {
        reviewService.adminUpdateVisible(id, Boolean.TRUE.equals(visible) ? 1 : 0);
        return R.ok();
    }

    @Override
    @DeleteMapping("/admin/{id}")
    public R<Void> adminDeleteReview(@PathVariable("id") Long id) {
        reviewService.adminDeleteReview(id);
        return R.ok();
    }
}
