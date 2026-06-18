package com.byw.api.review;

import com.byw.api.review.dto.ReviewStatsDTO;
import com.byw.common.core.result.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "byw-review", contextId = "reviewFeignClient")
public interface ReviewFeignClient {

    @GetMapping("/feign/review/stats/{productId}")
    R<ReviewStatsDTO> getReviewStats(@PathVariable("productId") Long productId);

    @GetMapping("/feign/review/exists/{orderNo}")
    R<Boolean> reviewExists(@PathVariable("orderNo") String orderNo);
}
