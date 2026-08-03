package com.byw.merchant.controller;

import com.byw.api.review.ReviewFeignClient;
import com.byw.common.core.result.PageResult;
import com.byw.common.core.result.R;
import com.byw.common.security.annotation.RequirePerm;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 商家端评价管理：仅本店评价（下游按 X-Shop-Id 过滤），支持回复。
 */
@RestController
@RequestMapping("/merchant/review")
@RequirePerm("m:review:manage")
@RequiredArgsConstructor
public class MerchantReviewController {

    private final ReviewFeignClient reviewFeignClient;

    @GetMapping("/list")
    public R<PageResult<Map<String, Object>>> list(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) Integer rating,
            @RequestParam(required = false) Integer status) {
        return reviewFeignClient.adminListReviews(pageNum, pageSize, rating, status);
    }

    /**
     * 回复评价（下游校验评价归属本店）
     */
    @PutMapping("/{id}/reply")
    public R<Void> reply(@PathVariable Long id, @RequestParam String content) {
        return reviewFeignClient.replyReview(id, content);
    }
}
