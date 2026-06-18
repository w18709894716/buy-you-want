package com.byw.order.feign;

import com.byw.api.order.OrderFeignClient;
import com.byw.api.order.dto.OrderCreateDTO;
import com.byw.api.order.dto.OrderDetailDTO;
import com.byw.common.core.result.R;
import com.byw.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/feign/order")
@RequiredArgsConstructor
public class OrderFeignImpl implements OrderFeignClient {

    private final OrderService orderService;

    @Override
    @PostMapping("/create")
    public R<String> createOrder(@RequestBody OrderCreateDTO createDTO) {
        String orderNo = orderService.createOrder(createDTO);
        return R.ok(orderNo);
    }

    @Override
    @GetMapping("/{orderNo}")
    public R<OrderDetailDTO> getOrderDetail(@PathVariable("orderNo") String orderNo) {
        return R.ok(orderService.getOrderDetail(orderNo));
    }

    @Override
    @PostMapping("/cancel/{orderNo}")
    public R<Boolean> cancelOrder(@PathVariable("orderNo") String orderNo) {
        orderService.cancelOrder(orderNo, "系统取消");
        return R.ok(true);
    }

    @Override
    @PostMapping("/update-status")
    public R<Boolean> updateOrderStatus(@RequestParam("orderNo") String orderNo, @RequestParam("status") Integer status) {
        orderService.updateStatus(orderNo, status);
        return R.ok(true);
    }
}
