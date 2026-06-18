package com.byw.pay.feign;

import com.byw.api.pay.PayFeignClient;
import com.byw.api.pay.dto.PayOrderDTO;
import com.byw.common.core.result.R;
import com.byw.pay.service.PayService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequestMapping("/feign/pay")
@RequiredArgsConstructor
public class PayFeignImpl implements PayFeignClient {

    private final PayService payService;

    @Override
    @PostMapping("/create")
    public R<PayOrderDTO> createPayOrder(@RequestParam("orderNo") String orderNo,
                                         @RequestParam("userId") Long userId,
                                         @RequestParam("amount") BigDecimal amount,
                                         @RequestParam("channel") String channel) {
        return R.ok(payService.createPayOrder(orderNo, userId, amount, channel));
    }

    @Override
    @GetMapping("/status/{payNo}")
    public R<Integer> getPayStatus(@PathVariable("payNo") String payNo) {
        return R.ok(payService.getPayStatus(payNo));
    }

    @Override
    @PostMapping("/refund")
    public R<Boolean> refund(@RequestParam("orderNo") String orderNo,
                             @RequestParam("amount") BigDecimal amount,
                             @RequestParam("reason") String reason) {
        payService.refund(orderNo, amount, reason);
        return R.ok(true);
    }
}
