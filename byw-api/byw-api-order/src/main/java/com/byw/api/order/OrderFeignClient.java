package com.byw.api.order;

import com.byw.api.order.dto.OrderCreateDTO;
import com.byw.api.order.dto.OrderDetailDTO;
import com.byw.common.core.result.PageResult;
import com.byw.common.core.result.R;
import lombok.Data;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

@FeignClient(name = "byw-order", contextId = "orderFeignClient")
public interface OrderFeignClient {

    @PostMapping("/feign/order/create")
    R<String> createOrder(@RequestBody OrderCreateDTO createDTO);

    @GetMapping("/feign/order/{orderNo}")
    R<OrderDetailDTO> getOrderDetail(@PathVariable("orderNo") String orderNo);

    @PostMapping("/feign/order/cancel/{orderNo}")
    R<Boolean> cancelOrder(@PathVariable("orderNo") String orderNo);

    @PostMapping("/feign/order/update-status")
    R<Boolean> updateOrderStatus(@RequestParam("orderNo") String orderNo, @RequestParam("status") Integer status);

    @PostMapping("/feign/order/ship-items")
    R<Boolean> shipItems(@RequestParam("orderNo") String orderNo,
                         @RequestParam("companyName") String companyName,
                         @RequestParam(value = "trackingNo", required = false) String trackingNo,
                         @RequestBody List<Long> itemIds);

    @GetMapping("/feign/order/list")
    R<PageResult<OrderDetailDTO>> listOrders(@RequestParam(value = "pageNum", defaultValue = "1") Integer pageNum,
                                             @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize,
                                             @RequestParam(value = "statuses", required = false) List<Integer> statuses,
                                             @RequestParam(value = "orderNo", required = false) String orderNo);

    @GetMapping("/feign/order/stats")
    R<OrderStatsDTO> getOrderStats();

    @GetMapping("/feign/order/today-stats")
    R<TodayOrderStatsDTO> getTodayOrderStats();

    @PostMapping("/feign/order/update-reviewed")
    R<Boolean> updateReviewed(@RequestParam("orderNo") String orderNo, @RequestParam("reviewed") Integer reviewed);

    @Data
    class OrderStatsDTO implements Serializable {
        private Long totalOrders;
        private BigDecimal totalRevenue;
        private Long pendingOrders;
    }

    @Data
    class TodayOrderStatsDTO implements Serializable {
        private Long todayOrders;
        private BigDecimal todayRevenue;
    }
}
