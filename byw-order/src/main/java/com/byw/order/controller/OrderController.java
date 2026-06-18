package com.byw.order.controller;

import com.byw.api.order.dto.OrderCreateDTO;
import com.byw.api.order.dto.OrderDetailDTO;
import com.byw.common.core.result.PageResult;
import com.byw.common.core.result.R;
import com.byw.common.security.annotation.RequireLogin;
import com.byw.common.security.context.UserContext;
import com.byw.order.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "订单", description = "订单管理")
@RestController
@RequestMapping("/order")
@RequiredArgsConstructor
@RequireLogin
public class OrderController {

    private final OrderService orderService;

    @Operation(summary = "创建订单")
    @PostMapping("/create")
    public R<String> create(@RequestBody OrderCreateDTO createDTO) {
        createDTO.setUserId(UserContext.getUserId());
        String orderNo = orderService.createOrder(createDTO);
        return R.ok(orderNo);
    }

    @Operation(summary = "获取订单详情")
    @GetMapping("/detail/{orderNo}")
    public R<OrderDetailDTO> detail(@PathVariable String orderNo) {
        return R.ok(orderService.getOrderDetail(orderNo));
    }

    @Operation(summary = "获取我的订单列表")
    @GetMapping("/my-orders")
    public R<PageResult<OrderDetailDTO>> myOrders(
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        Long userId = UserContext.getUserId();
        return R.ok(orderService.getUserOrders(userId, status, pageNum, pageSize));
    }

    @Operation(summary = "取消订单")
    @PostMapping("/cancel/{orderNo}")
    public R<Void> cancel(@PathVariable String orderNo, @RequestParam String reason) {
        orderService.cancelOrder(orderNo, reason);
        return R.ok();
    }

    @Operation(summary = "确认收货")
    @PostMapping("/confirm/{orderNo}")
    public R<Void> confirm(@PathVariable String orderNo) {
        orderService.confirmReceive(orderNo);
        return R.ok();
    }
}
