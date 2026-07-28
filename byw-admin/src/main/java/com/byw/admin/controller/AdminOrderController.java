package com.byw.admin.controller;

import com.byw.api.order.OrderFeignClient;
import com.byw.api.order.dto.OrderDetailDTO;
import com.byw.common.core.result.PageResult;
import com.byw.common.core.result.R;
import com.byw.common.security.annotation.RequireAdmin;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/order")
@RequireAdmin
@RequiredArgsConstructor
public class AdminOrderController {

    private final OrderFeignClient orderFeignClient;

    @GetMapping("/list")
    public R<PageResult<OrderDetailDTO>> list(@RequestParam(defaultValue = "1") Integer pageNum,
                                              @RequestParam(defaultValue = "10") Integer pageSize,
                                              @RequestParam(required = false) java.util.List<Integer> status,
                                              @RequestParam(required = false) String orderNo) {
        return orderFeignClient.listOrders(pageNum, pageSize, status, orderNo);
    }

    @GetMapping("/{orderNo}")
    public R<OrderDetailDTO> getOrderDetail(@PathVariable String orderNo) {
        return orderFeignClient.getOrderDetail(orderNo);
    }

    // 订单发货（整单/拆分）已迁移至商家后台 byw-merchant（MerchantOrderController），
    // 平台侧仅保留订单列表与详情的监管查看能力。
}
