package com.byw.review.feign;

import com.byw.api.review.ReviewFeignClient;
import com.byw.api.review.dto.ReviewStatsDTO;
import com.byw.common.core.result.PageResult;
import com.byw.common.core.result.R;
import com.byw.review.entity.Review;
import com.byw.review.service.ReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
        PageResult<Review> result = reviewService.adminListReviews(pageNum, pageSize, rating, status);
        // 转换为前端期望的字段格式
        List<Map<String, Object>> mappedList = result.getList().stream().map(r -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", r.getId());
            map.put("orderNo", r.getOrderNo());
            map.put("userId", r.getUserId());
            map.put("username", "用户" + r.getUserId());
            map.put("productId", r.getProductId());
            map.put("productName", "商品#" + r.getProductId());
            map.put("rating", r.getRating());
            map.put("content", r.getContent());
            map.put("hasImage", r.getHasImage());
            map.put("visible", r.getStatus() != null && r.getStatus() == 1);
            map.put("created", r.getCreatedAt() != null ? r.getCreatedAt().toString() : "");
            return map;
        }).collect(Collectors.toList());
        return R.ok(PageResult.of(mappedList, result.getTotal(), pageNum, pageSize));
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
