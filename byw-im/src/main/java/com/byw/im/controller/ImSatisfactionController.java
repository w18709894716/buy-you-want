package com.byw.im.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.byw.common.core.result.R;
import com.byw.common.security.annotation.RequireLogin;
import com.byw.common.security.context.UserContext;
import com.byw.im.entity.ServiceRecord;
import com.byw.im.service.ImService;
import com.byw.im.service.ServiceRecordService;
import com.byw.im.service.SatisfactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * IM 满意度评价 REST 接口（评价对象为服务记录 t_im_service_record，一次服务一次评价）。
 */
@Slf4j
@Tag(name = "客服满意度")
@RestController
@RequestMapping("/im/satisfaction")
@RequiredArgsConstructor
public class ImSatisfactionController {

    private final ServiceRecordService serviceRecordService;
    private final ImService imService;

    @Operation(summary = "提交评价（买家端）")
    @RequireLogin
    @PostMapping
    public R<ServiceRecord> submit(@RequestBody SubmitRequest request) {
        // 校验
        if (request.getConversationId() == null || request.getRating() == null) {
            return R.fail("会话ID和评分不能为空");
        }
        if (request.getRating() < 1 || request.getRating() > 5) {
            return R.fail("评分必须在 1-5 之间");
        }
        ServiceRecord saved = serviceRecordService.submitRating(
                request.getConversationId(), UserContext.getUserId(), UserContext.getShopId(),
                request.getRating(), request.getTags(), request.getComment());
        // 向会话插入“感谢评价”系统消息并广播（失败不影响评价结果）
        try {
            imService.notifySatisfactionSubmitted(saved.getConversationId(), saved.getRating());
        } catch (Exception e) {
            log.warn("满意度系统提示发送失败：conversationId={}", saved.getConversationId(), e);
        }
        return R.ok(saved);
    }

    @Operation(summary = "检查会话是否有可评价服务（买家端，true=可评价）")
    @RequireLogin
    @GetMapping("/check")
    public R<Boolean> check(@RequestParam Long conversationId) {
        return R.ok(serviceRecordService.latestRatable(conversationId, UserContext.getUserId()) != null);
    }

    @Operation(summary = "商家端评价列表分页")
    @RequireLogin
    @GetMapping("/list")
    public R<IPage<ServiceRecord>> list(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        Long shopId = UserContext.getShopId();
        if (shopId == null) {
            return R.fail("仅商家端可用");
        }
        return R.ok(serviceRecordService.listByShop(shopId, page, pageSize));
    }

    @Operation(summary = "商家端评分统计")
    @RequireLogin
    @GetMapping("/stats")
    public R<SatisfactionService.SatisfactionStats> stats() {
        Long shopId = UserContext.getShopId();
        if (shopId == null) {
            return R.fail("仅商家端可用");
        }
        return R.ok(serviceRecordService.stats(shopId));
    }

    @Data
    public static class SubmitRequest {
        private Long conversationId;
        private Integer rating;
        private String tags;
        private String comment;
    }
}