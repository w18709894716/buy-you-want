package com.byw.order.controller;

import com.byw.api.order.dto.OrderCreateDTO;
import com.byw.api.order.dto.OrderDetailDTO;
import com.byw.common.core.exception.BusinessException;
import com.byw.common.core.result.PageResult;
import com.byw.common.core.result.R;
import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.byw.common.security.annotation.RequireLogin;
import com.byw.common.security.context.UserContext;
import com.byw.order.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Tag(name = "订单", description = "订单管理")
@Slf4j
@RestController
@RequestMapping("/order")
@RequiredArgsConstructor
@RequireLogin
public class OrderController {

    private final OrderService orderService;

    @Operation(summary = "创建订单")
    @SentinelResource(value = "order:create", fallback = "createOrderFallback")
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
    @SentinelResource(value = "order:list", fallback = "myOrdersFallback")
    @GetMapping("/my-orders")
    public R<PageResult<OrderDetailDTO>> myOrders(
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Integer reviewed,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        Long userId = UserContext.getUserId();
        return R.ok(orderService.getUserOrders(userId, status, reviewed, pageNum, pageSize));
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

    @Operation(summary = "支付订单（模拟）")
    @PostMapping("/pay/{orderNo}")
    public R<Void> pay(@PathVariable String orderNo) {
        // TODO: 接入真实支付流程
        orderService.updateStatus(orderNo, 1);
        return R.ok();
    }

    @Operation(summary = "获取用户各状态订单数量")
    @GetMapping("/status-counts")
    public R<java.util.Map<Integer, Integer>> getStatusCounts() {
        Long userId = UserContext.getUserId();
        return R.ok(orderService.getOrderCountsByStatus(userId));
    }

    @Operation(summary = "更新订单评价状态")
    @PostMapping("/reviewed/{orderNo}")
    public R<Void> updateReviewed(@PathVariable String orderNo, @RequestParam Integer reviewed) {
        orderService.updateReviewed(orderNo, reviewed);
        return R.ok();
    }

    // ========== Sentinel fallback ==========
    private R<String> createOrderFallback(OrderCreateDTO createDTO, Throwable ex) {
        log.error("[order:create] 触发 fallback，userId={}, 异常: {}", createDTO.getUserId(), ex.getMessage(), ex);
        if (ex instanceof BusinessException) {
            return R.fail(ex.getMessage());
        }
        return R.fail("系统繁忙，请稍后再试");
    }
    private R<PageResult<OrderDetailDTO>> myOrdersFallback(Integer status, Integer reviewed, Integer pageNum, Integer pageSize, Throwable ex) {
        log.error("[order:list] 触发 fallback，异常: {}", ex.getMessage(), ex);
        if (ex instanceof BusinessException) {
            return R.fail(ex.getMessage());
        }
        return R.fail("系统繁忙，请稍后再试");
    }
}
