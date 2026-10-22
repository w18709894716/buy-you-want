package com.byw.order.controller;

import com.byw.api.order.dto.OrderCreateDTO;
import com.byw.api.order.dto.OrderDetailDTO;
import com.byw.api.pay.PayFeignClient;
import com.byw.api.pay.dto.PayOrderDTO;
import com.byw.common.core.exception.BusinessException;
import com.byw.common.core.exception.ResultCode;
import com.byw.common.core.result.PageResult;
import com.byw.common.core.result.R;
import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.byw.common.security.annotation.RequireLogin;
import com.byw.common.security.context.UserContext;
import com.byw.order.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
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
    private final PayFeignClient payFeignClient;

    /** 模拟支付渠道：两个模拟策略的 queryStatus 恒返回已支付，取哪个都一样 */
    private static final String MOCK_PAY_CHANNEL = "wechat";

    @Operation(summary = "创建订单")
    @SentinelResource(value = "order:create", fallback = "createOrderFallback")
    @PostMapping("/create")
    public R<String> create(@Valid @RequestBody OrderCreateDTO createDTO) {
        createDTO.setUserId(UserContext.getUserId());
        String orderNo = orderService.createOrder(createDTO);
        return R.ok(orderNo);
    }

    @Operation(summary = "获取订单详情")
    @GetMapping("/detail/{orderNo}")
    public R<OrderDetailDTO> detail(@PathVariable String orderNo) {
        OrderDetailDTO order = orderService.getOrderDetail(orderNo);
        assertOwner(order == null ? null : order.getUserId());
        return R.ok(order);
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
        assertOrderOwner(orderNo);
        orderService.cancelOrder(orderNo, reason);
        return R.ok();
    }

    @Operation(summary = "确认收货")
    @PostMapping("/confirm/{orderNo}")
    public R<Void> confirm(@PathVariable String orderNo) {
        assertOrderOwner(orderNo);
        orderService.confirmReceive(orderNo);
        return R.ok();
    }

    @Operation(summary = "支付订单（模拟）")
    @PostMapping("/pay/{orderNo}")
    public R<Void> pay(@PathVariable String orderNo) {
        Long userId = UserContext.getUserId();
        assertOrderOwner(orderNo);
        // 模拟支付必须经 byw-pay：原先直接 updateStatus(orderNo, 1) 不会生成 t_pay_order，
        // 这类订单后续取消/售后退款会因"未找到已支付的订单"永远退不出钱，
        // 同时还绕开了 byw.pay.mock-enabled 开关与支付事务消息落库路径。
        // 注意订单状态由支付结果消息异步流转到待发货，本接口返回成功不等于状态已变更。
        R<PayOrderDTO> payOrderResult = payFeignClient.createPayOrder(orderNo, userId, null, MOCK_PAY_CHANNEL);
        if (payOrderResult == null || !payOrderResult.isSuccess() || payOrderResult.getData() == null) {
            throw new BusinessException(message(payOrderResult, "发起支付失败"));
        }
        R<Boolean> mockResult = payFeignClient.mockPay(payOrderResult.getData().getPayNo(), userId);
        if (mockResult == null || !mockResult.isSuccess()) {
            throw new BusinessException(message(mockResult, "支付失败"));
        }
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
        assertOrderOwner(orderNo);
        orderService.updateReviewed(orderNo, reviewed);
        return R.ok();
    }

    /** 校验订单归属当前登录用户，不符抛 FORBIDDEN（防越权操作他人订单） */
    private void assertOrderOwner(String orderNo) {
        OrderDetailDTO order = orderService.getOrderDetail(orderNo);
        assertOwner(order == null ? null : order.getUserId());
    }

    private void assertOwner(Long ownerId) {
        if (ownerId == null || !ownerId.equals(UserContext.getUserId())) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
    }

    /** 透传下游失败原因（如"订单当前状态不可支付"），下游没给原因时用兜底文案 */
    private String message(R<?> result, String fallback) {
        if (result == null || result.getMessage() == null || result.getMessage().isEmpty()) {
            return fallback;
        }
        return result.getMessage();
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
