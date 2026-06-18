package com.byw.admin.controller;

import com.byw.api.order.OrderFeignClient;
import com.byw.api.order.dto.OrderDetailDTO;
import com.byw.common.core.result.R;
import com.byw.common.security.annotation.RequireAdmin;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/admin/order")
@RequireAdmin
@RequiredArgsConstructor
public class AdminOrderController {

    private final OrderFeignClient orderFeignClient;

    @GetMapping("/list")
    public R<Map<String, Object>> list(@RequestParam(defaultValue = "1") Integer pageNum,
                                        @RequestParam(defaultValue = "10") Integer pageSize) {
        // TODO: 实现订单列表查询
        Map<String, Object> result = new HashMap<>();
        result.put("list", java.util.Collections.emptyList());
        result.put("total", 0);
        return R.ok(result);
    }

    @GetMapping("/{orderNo}")
    public R<OrderDetailDTO> getOrderDetail(@PathVariable String orderNo) {
        return orderFeignClient.getOrderDetail(orderNo);
    }
}
