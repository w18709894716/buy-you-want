package com.byw.promotion.controller;

import com.byw.common.core.result.R;
import com.byw.common.security.annotation.RequireLogin;
import com.byw.common.security.context.UserContext;
import com.byw.promotion.entity.SeckillActivity;
import com.byw.promotion.service.SeckillService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "秒杀", description = "秒杀活动管理")
@RestController
@RequestMapping("/seckill")
@RequiredArgsConstructor
public class SeckillController {

    private final SeckillService seckillService;

    @Operation(summary = "获取秒杀活动列表")
    @GetMapping("/list")
    public R<List<SeckillActivity>> list() {
        return R.ok(seckillService.getSeckillList());
    }

    @Operation(summary = "参与秒杀")
    @PostMapping("/seckill/{activityId}")
    @RequireLogin
    public R<Void> seckill(@PathVariable Long activityId) {
        seckillService.seckill(activityId, UserContext.getUserId());
        return R.ok();
    }
}
