package com.byw.admin.controller;

import com.byw.api.review.ReviewFeignClient;
import com.byw.common.core.result.PageResult;
import com.byw.common.core.result.R;
import com.byw.common.security.annotation.RequireAdmin;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/admin/review")
@RequireAdmin
@RequiredArgsConstructor
public class AdminReviewController {

    private final ReviewFeignClient reviewFeignClient;

    @GetMapping("/list")
    public R<PageResult<Map<String, Object>>> list(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) Integer rating,
            @RequestParam(required = false) Boolean visible) {
        // visible=true → status=1, visible=false → status=0, null → 不筛选
        Integer status = visible == null ? null : (visible ? 1 : 0);
        return reviewFeignClient.adminListReviews(page, pageSize, rating, status);
    }

    @PutMapping("/{id}/visible")
    public R<Void> toggleVisible(@PathVariable Long id, @RequestParam Boolean visible) {
        return reviewFeignClient.adminUpdateVisible(id, visible);
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        return reviewFeignClient.adminDeleteReview(id);
    }
}
