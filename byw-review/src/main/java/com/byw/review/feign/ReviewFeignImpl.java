package com.byw.review.feign;

import com.byw.api.review.ReviewFeignClient;
import com.byw.api.review.dto.ReviewStatsDTO;
import com.byw.common.core.result.R;
import com.byw.review.service.ReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

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
}
