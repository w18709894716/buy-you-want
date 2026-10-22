package com.byw.pay.feign;

import com.byw.api.pay.PayFeignClient;
import com.byw.api.pay.dto.PayOrderDTO;
import com.byw.api.pay.dto.RefundInfoDTO;
import com.byw.common.core.result.R;
import com.byw.common.security.annotation.InternalApi;
import com.byw.pay.service.PayService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequestMapping("/feign/pay")
@RequiredArgsConstructor
@InternalApi
public class PayFeignImpl implements PayFeignClient {

    private final PayService payService;

    @Override
    @PostMapping("/create")
    public R<PayOrderDTO> createPayOrder(@RequestParam("orderNo") String orderNo,
                                         @RequestParam("userId") Long userId,
                                         // 可缺省：金额一律以订单 payAmount 为准，传了才做一致性核对。
                                         // Feign 侧传 null 会直接省略该查询参数，服务端必须允许缺省。
                                         @RequestParam(value = "amount", required = false) BigDecimal amount,
                                         @RequestParam("channel") String channel) {
        return R.ok(payService.createPayOrder(orderNo, userId, amount, channel));
    }

    @Override
    @GetMapping("/status/{payNo}")
    public R<Integer> getPayStatus(@PathVariable("payNo") String payNo) {
        return R.ok(payService.getPayStatus(payNo));
    }

    @Override
    @PostMapping("/mock-pay")
    public R<Boolean> mockPay(@RequestParam("payNo") String payNo,
                              @RequestParam("userId") Long userId) {
        payService.mockPay(payNo, userId);
        return R.ok(true);
    }

    @Override
    @PostMapping("/refund")
    public R<Boolean> refund(@RequestParam("orderNo") String orderNo,
                             @RequestParam("amount") BigDecimal amount,
                             @RequestParam("reason") String reason,
                             @RequestParam(value = "refNo", required = false) String refNo) {
        payService.refund(orderNo, amount, reason, refNo);
        return R.ok(true);
    }

    @Override
    @GetMapping("/refund-detail")
    public R<RefundInfoDTO> getRefundByOrderNo(@RequestParam("orderNo") String orderNo) {
        return R.ok(payService.getRefundByOrderNo(orderNo));
    }
}
