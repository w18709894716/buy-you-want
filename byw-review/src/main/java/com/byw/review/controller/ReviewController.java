package com.byw.review.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import com.byw.api.review.dto.ReviewStatsDTO;
import com.byw.api.user.UserFeignClient;
import com.byw.api.user.dto.UserDTO;
import com.byw.common.core.exception.BusinessException;
import com.byw.common.core.result.PageResult;
import com.byw.common.core.result.R;
import com.byw.common.security.annotation.Public;
import com.byw.common.security.annotation.RequireLogin;
import com.byw.common.security.context.UserContext;
import com.byw.review.document.ReviewDetail;
import com.byw.review.service.ReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Tag(name = "评价", description = "评价管理")
@RestController
@RequestMapping("/review")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;
    private final UserFeignClient userFeignClient;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Operation(summary = "创建评价")
    @RequireLogin
    @PostMapping("/create")
    public R<Void> create(@Valid @RequestBody CreateReviewRequest request) {
        ReviewDetail detail = new ReviewDetail();
        detail.setOrderId(request.getOrderNo());
        detail.setProductId(request.getProductId());
        // skuId 必填：t_review.sku_id 是 NOT NULL，缺省会直接插入失败
        detail.setSkuId(request.getSkuId());
        detail.setUserId(UserContext.getUserId());
        detail.setRating(request.getRating());
        detail.setContent(request.getContent());
        detail.setImages(request.getImages());
        detail.setVideos(request.getVideos());

        reviewService.createReview(detail, request.getRating(), request.getContent(), request.getIsAnonymous());
        return R.ok();
    }

    @Operation(summary = "追加评价")
    @RequireLogin
    @PostMapping("/append")
    public R<Void> append(@Valid @RequestBody AppendReviewRequest request) {
        reviewService.appendReview(request.getOrderNo(), UserContext.getUserId(), request.getSkuId(),
                request.getAppendContent(), request.getAppendImages());
        return R.ok();
    }

    @Operation(summary = "获取商品评价列表")
    @GetMapping("/product/{productId}")
    @Public
    public R<PageResult<ProductReviewVO>> getByProduct(
            @PathVariable Long productId,
            @RequestParam(required = false) Integer rating,
            @RequestParam(required = false) Boolean hasImage,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        PageResult<ReviewDetail> page = reviewService.getReviewsByProduct(productId, rating, hasImage, pageNum, pageSize);
        Map<Long, String> nameCache = new HashMap<>();
        List<ProductReviewVO> voList = new java.util.ArrayList<>();
        for (ReviewDetail d : page.getList()) {
            ProductReviewVO vo = new ProductReviewVO();
            vo.setId(d.getId());
            vo.setRating(d.getRating());
            vo.setContent(d.getContent());
            vo.setImages(d.getImages());
            vo.setSkuId(d.getSkuId());
            vo.setDate(d.getCreatedAt() != null ? d.getCreatedAt().format(DATE_FMT) : "");
            boolean anonymous = d.getIsAnonymous() != null && d.getIsAnonymous() == 1;
            String rawName = resolveRawName(d.getUserId(), nameCache);
            if (rawName == null) {
                vo.setUsername("匿名用户");
            } else {
                vo.setUsername(anonymous ? maskName(rawName) : rawName);
            }
            vo.setAppendContent(d.getAppendContent());
            vo.setAppendImages(d.getAppendImages());
            vo.setMerchantReply(d.getMerchantReply());
            voList.add(vo);
        }
        return R.ok(PageResult.of(voList, page.getTotal(), pageNum, pageSize));
    }

    /** 获取用户原始展示名（昵称优先，其次用户名）；查询失败返回 null。同一用户缓存避免重复调用 */
    private String resolveRawName(Long userId, Map<Long, String> cache) {
        if (userId == null) return null;
        if (cache.containsKey(userId)) return cache.get(userId);
        String raw = null;
        try {
            R<UserDTO> r = userFeignClient.getUserById(userId);
            if (r.isSuccess() && r.getData() != null) {
                UserDTO u = r.getData();
                raw = u.getNickname() != null && !u.getNickname().isBlank() ? u.getNickname() : u.getUsername();
            }
        } catch (Exception ignored) {
        }
        cache.put(userId, raw);
        return raw;
    }

    /** 用户名脱敏：保留首尾字符，中间以 * 代替 */
    private String maskName(String name) {
        if (name == null || name.isBlank()) return "匿名用户";
        name = name.trim();
        if (name.length() == 1) return name + "**";
        if (name.length() == 2) return name.charAt(0) + "*";
        return name.charAt(0) + "***" + name.charAt(name.length() - 1);
    }

    @Operation(summary = "获取商品评价统计")
    @GetMapping("/stats/{productId}")
    @Public
    public R<ReviewStatsDTO> getStats(@PathVariable Long productId) {
        return R.ok(reviewService.getReviewStats(productId));
    }

    @Operation(summary = "检查订单是否已评价")
    @RequireLogin
    @GetMapping("/exists/{orderNo}")
    public R<Boolean> exists(@PathVariable String orderNo) {
        // 按登录用户维度查重，避免任意登录用户探测他人订单的评价状态
        return R.ok(reviewService.reviewExists(orderNo, UserContext.getUserId()));
    }

    @Operation(summary = "获取本人该订单的评价明细")
    @RequireLogin
    @GetMapping("/order/{orderNo}")
    public R<List<OrderReviewVO>> getByOrder(@PathVariable String orderNo) {
        List<ReviewDetail> details = reviewService.getOrderReviews(orderNo, UserContext.getUserId());
        List<OrderReviewVO> voList = new java.util.ArrayList<>();
        for (ReviewDetail d : details) {
            OrderReviewVO vo = new OrderReviewVO();
            vo.setProductId(d.getProductId());
            vo.setSkuId(d.getSkuId());
            vo.setRating(d.getRating());
            vo.setContent(d.getContent());
            vo.setImages(d.getImages());
            vo.setAppendContent(d.getAppendContent());
            vo.setAppendImages(d.getAppendImages());
            vo.setMerchantReply(d.getMerchantReply());
            voList.add(vo);
        }
        return R.ok(voList);
    }

    @Operation(summary = "批量创建评价")
    @RequireLogin
    @PostMapping("/create-batch")
    public R<Void> createBatch(@Valid @RequestBody BatchReviewRequest request) {
        // 一次提交只能针对同一个订单：服务端按首个 orderNo 归一，混单会被静默改写到错误的订单上
        String orderNo = request.getReviews().get(0).getOrderNo();
        List<ReviewDetail> details = new java.util.ArrayList<>();
        for (ReviewItem item : request.getReviews()) {
            if (!orderNo.equals(item.getOrderNo())) {
                throw new BusinessException("一次只能评价同一个订单的商品");
            }
            ReviewDetail detail = new ReviewDetail();
            detail.setOrderId(orderNo);
            detail.setProductId(item.getProductId());
            detail.setSkuId(item.getSkuId());
            detail.setUserId(UserContext.getUserId());
            detail.setRating(item.getRating());
            detail.setContent(item.getContent());
            detail.setImages(item.getImages());
            detail.setIsAnonymous(item.getIsAnonymous());
            details.add(detail);
        }
        reviewService.createBatchReviews(UserContext.getUserId(), orderNo, details);
        return R.ok();
    }

    @Operation(summary = "获取我的评价列表")
    @RequireLogin
    @GetMapping("/my-reviews")
    public R<PageResult<Map<String, Object>>> myReviews(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) Boolean hasImage) {
        return R.ok(reviewService.getUserReviews(UserContext.getUserId(), pageNum, pageSize, hasImage));
    }

    @Data
    public static class CreateReviewRequest {
        @NotBlank(message = "订单号不能为空")
        @Size(max = 64, message = "订单号过长")
        private String orderNo;
        @NotNull(message = "商品ID不能为空")
        private Long productId;
        @NotNull(message = "SKU不能为空")
        private Long skuId;
        @NotNull(message = "请选择评分")
        @Min(value = 1, message = "评分只能是 1~5")
        @Max(value = 5, message = "评分只能是 1~5")
        private Integer rating;
        @Size(max = 1000, message = "评价内容过长")
        private String content;
        private List<String> images;
        private List<String> videos;
        private Integer isAnonymous;
    }

    @Data
    public static class BatchReviewRequest {
        @NotEmpty(message = "评价列表不能为空")
        @Valid
        private List<ReviewItem> reviews;
    }

    @Data
    public static class ReviewItem {
        @NotBlank(message = "订单号不能为空")
        @Size(max = 64, message = "订单号过长")
        private String orderNo;
        @NotNull(message = "商品ID不能为空")
        private Long productId;
        @NotNull(message = "SKU不能为空")
        private Long skuId;
        @NotNull(message = "请选择评分")
        @Min(value = 1, message = "评分只能是 1~5")
        @Max(value = 5, message = "评分只能是 1~5")
        private Integer rating;
        @Size(max = 1000, message = "评价内容过长")
        private String content;
        private List<String> images;
        private Integer isAnonymous;
    }

    @Data
    public static class AppendReviewRequest {
        @NotBlank(message = "订单号不能为空")
        @Size(max = 64, message = "订单号过长")
        private String orderNo;
        @NotNull(message = "SKU不能为空")
        private Long skuId;
        @Size(max = 1000, message = "追评内容过长")
        private String appendContent;
        private List<String> appendImages;
    }

    @Data
    public static class ProductReviewVO {
        private String id;
        private String username;
        private String date;
        private Integer rating;
        private String content;
        private List<String> images;
        private Long skuId;
        private String appendContent;
        private List<String> appendImages;
        private String merchantReply;
    }

    @Data
    public static class OrderReviewVO {
        private Long productId;
        private Long skuId;
        private Integer rating;
        private String content;
        private List<String> images;
        private String appendContent;
        private List<String> appendImages;
        private String merchantReply;
    }
}
