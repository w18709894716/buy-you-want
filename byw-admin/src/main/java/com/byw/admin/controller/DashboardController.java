package com.byw.admin.controller;

import com.byw.common.core.result.R;
import com.byw.common.security.annotation.RequireAdmin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/admin/dashboard")
@RequireAdmin
public class DashboardController {

    @GetMapping("/stats")
    public R<Map<String, Object>> getStats() {
        // TODO: 从各个微服务聚合真实统计数据
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalUsers", 0L);
        stats.put("totalOrders", 0L);
        stats.put("totalRevenue", BigDecimal.ZERO);
        stats.put("todayOrders", 0L);
        stats.put("todayRevenue", BigDecimal.ZERO);
        stats.put("todayNewUsers", 0L);
        stats.put("totalProducts", 0L);
        stats.put("pendingOrders", 0L);
        return R.ok(stats);
    }
}
