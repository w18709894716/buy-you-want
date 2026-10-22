package com.byw.admin.controller;

import com.byw.api.order.OrderFeignClient;
import com.byw.api.product.ProductFeignClient;
import com.byw.api.user.UserFeignClient;
import com.byw.common.core.result.R;
import com.byw.common.security.annotation.RequireAdmin;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * 平台端控制台概览。
 *
 * <p>无 @RequirePerm 是因为"控制台"菜单（t_sys_menu id=100）perm_code 为 NULL，
 * 没有任何权限标识可校验；但绝不能因此退回"仅需登录"——默认 fail-closed 只保证已认证，
 * C 端会员的 token 同样算已登录，能直接读平台成交额与订单量。
 * 这里用 @RequireAdmin 收口到平台员工（登录时 role 统一写 platform_admin，见 AuthServiceImpl）。
 */
@RestController
@RequestMapping("/admin/dashboard")
@RequireAdmin
@RequiredArgsConstructor
public class DashboardController {

    private final UserFeignClient userFeignClient;
    private final OrderFeignClient orderFeignClient;
    private final ProductFeignClient productFeignClient;

    @GetMapping("/stats")
    public R<Map<String, Object>> getStats() {
        Map<String, Object> stats = new HashMap<>();

        // 用户统计
        R<Long> totalUsersResult = userFeignClient.countUsers();
        R<Long> todayNewUsersResult = userFeignClient.countTodayNewUsers();
        stats.put("totalUsers", totalUsersResult.isSuccess() ? totalUsersResult.getData() : 0L);
        stats.put("todayNewUsers", todayNewUsersResult.isSuccess() ? todayNewUsersResult.getData() : 0L);

        // 订单统计
        R<OrderFeignClient.OrderStatsDTO> orderStatsResult = orderFeignClient.getOrderStats();
        R<OrderFeignClient.TodayOrderStatsDTO> todayOrderStatsResult = orderFeignClient.getTodayOrderStats();
        
        if (orderStatsResult.isSuccess() && orderStatsResult.getData() != null) {
            OrderFeignClient.OrderStatsDTO orderStats = orderStatsResult.getData();
            stats.put("totalOrders", orderStats.getTotalOrders());
            stats.put("totalRevenue", orderStats.getTotalRevenue());
            stats.put("pendingOrders", orderStats.getPendingOrders());
        } else {
            stats.put("totalOrders", 0L);
            stats.put("totalRevenue", BigDecimal.ZERO);
            stats.put("pendingOrders", 0L);
        }

        if (todayOrderStatsResult.isSuccess() && todayOrderStatsResult.getData() != null) {
            OrderFeignClient.TodayOrderStatsDTO todayStats = todayOrderStatsResult.getData();
            stats.put("todayOrders", todayStats.getTodayOrders());
            stats.put("todayRevenue", todayStats.getTodayRevenue());
        } else {
            stats.put("todayOrders", 0L);
            stats.put("todayRevenue", BigDecimal.ZERO);
        }

        // 商品统计
        R<Long> totalProductsResult = productFeignClient.countProducts();
        stats.put("totalProducts", totalProductsResult.isSuccess() ? totalProductsResult.getData() : 0L);

        return R.ok(stats);
    }
}
