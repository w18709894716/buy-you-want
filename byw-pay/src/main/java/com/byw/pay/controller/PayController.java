package com.byw.pay.controller;

import com.byw.api.pay.dto.PayOrderDTO;
import com.byw.common.core.result.R;
import com.byw.common.security.annotation.RequireLogin;
import com.byw.common.security.context.UserContext;
import com.byw.pay.service.PayService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@Tag(name = "支付", description = "支付管理")
@RestController
@RequestMapping("/pay")
@RequiredArgsConstructor
@RequireLogin
public class PayController {

    private final PayService payService;

    @Operation(summary = "创建支付单")
    @PostMapping("/create")
    public R<PayOrderDTO> create(@RequestParam String orderNo,
                                 @RequestParam BigDecimal amount,
                                 @RequestParam String channel) {
        Long userId = UserContext.getUserId();
        return R.ok(payService.createPayOrder(orderNo, userId, amount, channel));
    }

    @Operation(summary = "查询支付状态")
    @GetMapping("/status/{payNo}")
    public R<Integer> status(@PathVariable String payNo) {
        return R.ok(payService.getPayStatus(payNo));
    }

    @Operation(summary = "支付回调")
    @PostMapping("/callback/{channel}")
    public R<Void> callback(@PathVariable String channel,
                            @RequestParam String payNo,
                            @RequestParam String tradeNo,
                            @RequestBody(required = false) String callbackContent) {
        payService.handlePayCallback(channel, payNo, tradeNo, callbackContent);
        return R.ok();
    }

    @Operation(summary = "退款")
    @PostMapping("/refund")
    public R<Void> refund(@RequestParam String orderNo,
                          @RequestParam BigDecimal amount,
                          @RequestParam String reason) {
        payService.refund(orderNo, amount, reason);
        return R.ok();
    }
}
