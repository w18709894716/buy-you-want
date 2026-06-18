package com.byw.promotion.controller;

import com.byw.common.core.result.R;
import com.byw.common.security.annotation.RequireLogin;
import com.byw.common.security.context.UserContext;
import com.byw.promotion.entity.GroupBuyActivity;
import com.byw.promotion.service.GroupBuyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "拼团", description = "拼团活动管理")
@RestController
@RequestMapping("/group-buy")
@RequiredArgsConstructor
public class GroupBuyController {

    private final GroupBuyService groupBuyService;

    @Operation(summary = "获取拼团活动列表")
    @GetMapping("/list")
    public R<List<GroupBuyActivity>> list() {
        return R.ok(groupBuyService.listActivities());
    }

    @Operation(summary = "参加拼团")
    @PostMapping("/join")
    @RequireLogin
    public R<Void> join(@RequestParam Long activityId) {
        groupBuyService.joinActivity(activityId, UserContext.getUserId());
        return R.ok();
    }
}
