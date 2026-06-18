package com.byw.api.logistics;

import com.byw.api.logistics.dto.LogisticsDTO;
import com.byw.api.logistics.dto.ShipRequestDTO;
import com.byw.common.core.result.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(name = "byw-logistics", contextId = "logisticsFeignClient")
public interface LogisticsFeignClient {

    @PostMapping("/feign/logistics/ship")
    R<LogisticsDTO> ship(@RequestBody ShipRequestDTO request);

    @GetMapping("/feign/logistics/track/{orderNo}")
    R<LogisticsDTO> track(@PathVariable("orderNo") String orderNo);
}
