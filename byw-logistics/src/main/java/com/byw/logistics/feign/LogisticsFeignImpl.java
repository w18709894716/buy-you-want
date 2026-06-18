package com.byw.logistics.feign;

import com.byw.api.logistics.LogisticsFeignClient;
import com.byw.api.logistics.dto.LogisticsDTO;
import com.byw.api.logistics.dto.ShipRequestDTO;
import com.byw.common.core.result.R;
import com.byw.logistics.service.LogisticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/feign/logistics")
@RequiredArgsConstructor
public class LogisticsFeignImpl implements LogisticsFeignClient {

    private final LogisticsService logisticsService;

    @Override
    @PostMapping("/ship")
    public R<LogisticsDTO> ship(@RequestBody ShipRequestDTO request) {
        return R.ok(logisticsService.ship(request));
    }

    @Override
    @GetMapping("/track/{orderNo}")
    public R<LogisticsDTO> track(@PathVariable("orderNo") String orderNo) {
        return R.ok(logisticsService.track(orderNo));
    }
}
