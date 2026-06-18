package com.byw.api.order;

import com.byw.api.order.dto.OrderCreateDTO;
import com.byw.api.order.dto.OrderDetailDTO;
import com.byw.common.core.result.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(name = "byw-order", contextId = "orderFeignClient")
public interface OrderFeignClient {

    @PostMapping("/feign/order/create")
    R<String> createOrder(@RequestBody OrderCreateDTO createDTO);

    @GetMapping("/feign/order/{orderNo}")
    R<OrderDetailDTO> getOrderDetail(@PathVariable("orderNo") String orderNo);

    @PostMapping("/feign/order/cancel/{orderNo}")
    R<Boolean> cancelOrder(@PathVariable("orderNo") String orderNo);

    @PostMapping("/feign/order/update-status")
    R<Boolean> updateOrderStatus(@RequestParam("orderNo") String orderNo, @RequestParam("status") Integer status);
}
