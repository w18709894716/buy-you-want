package com.byw.api.review;

import com.byw.api.review.dto.ReviewStatsDTO;
import com.byw.common.core.result.PageResult;
import com.byw.common.core.result.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@FeignClient(name = "byw-review", contextId = "reviewFeignClient")
public interface ReviewFeignClient {

    @GetMapping("/feign/review/stats/{productId}")
    R<ReviewStatsDTO> getReviewStats(@PathVariable("productId") Long productId);

    @GetMapping("/feign/review/exists/{orderNo}")
    R<Boolean> reviewExists(@PathVariable("orderNo") String orderNo);

    @GetMapping("/feign/review/admin/list")
    R<PageResult<Map<String, Object>>> adminListReviews(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) Integer rating,
            @RequestParam(required = false) Integer status);

    @PutMapping("/feign/review/admin/{id}/visible")
    R<Void> adminUpdateVisible(@PathVariable("id") Long id, @RequestParam("visible") Boolean visible);

    @DeleteMapping("/feign/review/admin/{id}")
    R<Void> adminDeleteReview(@PathVariable("id") Long id);
}
