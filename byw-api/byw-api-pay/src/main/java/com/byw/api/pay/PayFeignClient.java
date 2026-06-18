package com.byw.api.pay;

import com.byw.api.pay.dto.PayOrderDTO;
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

    @PostMapping("/feign/pay/refund")
    R<Boolean> refund(@RequestParam("orderNo") String orderNo,
                      @RequestParam("amount") java.math.BigDecimal amount,
                      @RequestParam("reason") String reason);
}
