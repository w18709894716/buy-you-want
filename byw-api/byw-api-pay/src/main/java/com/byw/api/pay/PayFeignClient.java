package com.byw.api.pay;

import com.byw.api.pay.dto.PayOrderDTO;
import com.byw.api.pay.dto.RefundInfoDTO;
import com.byw.common.core.result.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(name = "byw-pay", contextId = "payFeignClient")
public interface PayFeignClient {

    @PostMapping("/feign/pay/create")
    R<PayOrderDTO> createPayOrder(@RequestParam("orderNo") String orderNo,
                                  @RequestParam("userId") Long userId,
                                  @RequestParam("amount") java.math.BigDecimal amount,
                                  @RequestParam("channel") String channel);

    @GetMapping("/feign/pay/status/{payNo}")
    R<Integer> getPayStatus(@PathVariable("payNo") String payNo);

    /**
     * 模拟支付：把已建好的支付单置为已支付，走与真实回调相同的落库 + 事务消息路径。
     * 仅 {@code byw.pay.mock-enabled=true} 时可用，生产关掉开关后返回 403。
     */
    @PostMapping("/feign/pay/mock-pay")
    R<Boolean> mockPay(@RequestParam("payNo") String payNo,
                       @RequestParam("userId") Long userId);

    /**
     * 退款。refNo 为业务幂等键（售后单号），同一 refNo 重复调用只退一次；
     * 管理端手工退款可不传。
     */
    @PostMapping("/feign/pay/refund")
    R<Boolean> refund(@RequestParam("orderNo") String orderNo,
                      @RequestParam("amount") java.math.BigDecimal amount,
                      @RequestParam("reason") String reason,
                      @RequestParam(value = "refNo", required = false) String refNo);

    @GetMapping("/feign/pay/refund-detail")
    R<RefundInfoDTO> getRefundByOrderNo(@RequestParam("orderNo") String orderNo);
}
