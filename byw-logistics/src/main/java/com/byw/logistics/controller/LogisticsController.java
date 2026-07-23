package com.byw.logistics.controller;

import com.byw.api.logistics.dto.LogisticsDTO;
import com.byw.api.logistics.dto.ShipRequestDTO;
import com.byw.common.core.result.R;
import com.byw.common.security.annotation.RequireAdmin;
import com.byw.logistics.entity.LogisticsTrace;
import com.byw.logistics.service.LogisticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "物流", description = "物流管理")
@RestController
@RequestMapping("/logistics")
@RequiredArgsConstructor
public class LogisticsController {

    private final LogisticsService logisticsService;

    @Operation(summary = "发货")
    @PostMapping("/ship")
    @RequireAdmin
    public R<LogisticsDTO> ship(@RequestBody ShipRequestDTO request) {
        return R.ok(logisticsService.ship(request));
    }

    @Operation(summary = "查询物流轨迹")
    @GetMapping("/track/{orderNo}")
    public R<LogisticsDTO> track(@PathVariable String orderNo) {
        return R.ok(logisticsService.track(orderNo));
    }

    @Operation(summary = "查询订单全部物流包裹")
    @GetMapping("/track-all/{orderNo}")
    public R<java.util.List<LogisticsDTO>> trackAll(@PathVariable String orderNo) {
        return R.ok(logisticsService.trackAll(orderNo));
    }

    @Operation(summary = "更新物流轨迹")
    @PostMapping("/trace/{logisticsId}")
    @RequireAdmin
    public R<Void> updateTrace(@PathVariable Long logisticsId, @RequestBody LogisticsTrace trace) {
        logisticsService.updateTrace(logisticsId, trace);
        return R.ok();
    }
}
