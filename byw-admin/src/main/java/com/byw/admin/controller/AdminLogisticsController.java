package com.byw.admin.controller;

import com.byw.api.logistics.LogisticsFeignClient;
import com.byw.common.core.result.PageResult;
import com.byw.common.core.result.R;
import com.byw.common.security.annotation.RequireAdmin;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin/logistics")
@RequireAdmin
@RequiredArgsConstructor
public class AdminLogisticsController {

    private final LogisticsFeignClient logisticsFeignClient;

    @GetMapping("/list")
    public R<PageResult<Map<String, Object>>> list(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String orderNo,
            @RequestParam(required = false) Integer status) {
        return logisticsFeignClient.adminListLogistics(pageNum, pageSize, orderNo, status);
    }

    @GetMapping("/{id}/trace")
    public R<List<Map<String, Object>>> trace(@PathVariable Long id) {
        return logisticsFeignClient.adminGetTrace(id);
    }
}
