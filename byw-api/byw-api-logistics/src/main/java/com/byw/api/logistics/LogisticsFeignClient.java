package com.byw.api.logistics;

import com.byw.api.logistics.dto.LogisticsDTO;
import com.byw.api.logistics.dto.ShipRequestDTO;
import com.byw.common.core.result.PageResult;
import com.byw.common.core.result.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@FeignClient(name = "byw-logistics", contextId = "logisticsFeignClient")
public interface LogisticsFeignClient {

    @PostMapping("/feign/logistics/ship")
    R<LogisticsDTO> ship(@RequestBody ShipRequestDTO request);

    @GetMapping("/feign/logistics/track/{orderNo}")
    R<LogisticsDTO> track(@PathVariable("orderNo") String orderNo);

    @GetMapping("/feign/logistics/admin/list")
    R<PageResult<Map<String, Object>>> adminListLogistics(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String orderNo,
            @RequestParam(required = false) Integer status);

    @GetMapping("/feign/logistics/admin/{id}/trace")
    R<java.util.List<Map<String, Object>>> adminGetTrace(@PathVariable("id") Long id);
}
