package com.byw.logistics.feign;

import com.byw.api.logistics.LogisticsFeignClient;
import com.byw.api.logistics.dto.LogisticsDTO;
import com.byw.api.logistics.dto.ShipRequestDTO;
import com.byw.common.core.result.PageResult;
import com.byw.common.core.result.R;
import com.byw.logistics.entity.LogisticsOrder;
import com.byw.logistics.entity.LogisticsTrace;
import com.byw.logistics.service.LogisticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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

    @Override
    @GetMapping("/admin/list")
    public R<PageResult<Map<String, Object>>> adminListLogistics(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String orderNo,
            @RequestParam(required = false) Integer status) {
        PageResult<LogisticsOrder> result = logisticsService.adminListLogistics(pageNum, pageSize, orderNo, status);
        List<Map<String, Object>> mappedList = result.getList().stream().map(o -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", o.getId());
            map.put("orderNo", o.getOrderNo());
            map.put("company", o.getCompanyName());
            map.put("trackingNo", o.getTrackingNo());
            map.put("status", o.getStatus());
            map.put("latestInfo", "");
            map.put("updatedTime", o.getUpdatedAt() != null ? o.getUpdatedAt().toString() : "");
            return map;
        }).collect(Collectors.toList());
        return R.ok(PageResult.of(mappedList, result.getTotal(), pageNum, pageSize));
    }

    @Override
    @GetMapping("/admin/{id}/trace")
    public R<List<Map<String, Object>>> adminGetTrace(@PathVariable("id") Long id) {
        List<LogisticsTrace> traces = logisticsService.adminGetTrace(id);
        List<Map<String, Object>> mappedList = traces.stream().map(t -> {
            Map<String, Object> map = new HashMap<>();
            map.put("traceTime", t.getTraceTime() != null ? t.getTraceTime().toString() : "");
            map.put("createdAt", t.getCreatedAt() != null ? t.getCreatedAt().toString() : "");
            map.put("description", t.getDescription());
            map.put("location", t.getLocation());
            return map;
        }).collect(Collectors.toList());
        return R.ok(mappedList);
    }
}
