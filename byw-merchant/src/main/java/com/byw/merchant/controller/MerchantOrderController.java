package com.byw.merchant.controller;

import com.byw.api.order.OrderFeignClient;
import com.byw.api.order.dto.OrderDetailDTO;
import com.byw.common.core.result.PageResult;
import com.byw.common.core.result.R;
import com.byw.common.security.annotation.RequirePerm;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 商家端订单管理：仅本店订单（下游按 X-Shop-Id 过滤），支持发货。
 */
@RestController
@RequestMapping("/merchant/order")
@RequirePerm("m:order:list")
@RequiredArgsConstructor
public class MerchantOrderController {

    private final OrderFeignClient orderFeignClient;

    @GetMapping("/list")
    public R<PageResult<OrderDetailDTO>> list(@RequestParam(defaultValue = "1") Integer pageNum,
                                              @RequestParam(defaultValue = "10") Integer pageSize,
                                              @RequestParam(required = false) List<Integer> status,
                                              @RequestParam(required = false) String orderNo) {
        return orderFeignClient.listOrders(pageNum, pageSize, status, orderNo);
    }

    @GetMapping("/{orderNo}")
    public R<OrderDetailDTO> getOrderDetail(@PathVariable String orderNo) {
        return orderFeignClient.getOrderDetail(orderNo);
    }

    /**
     * 订单发货
     */
    @PutMapping("/{orderNo}/ship")
    @RequirePerm("m:order:ship")
    public R<Boolean> ship(@PathVariable String orderNo,
                           @RequestParam(required = false) String company,
                           @RequestParam(required = false) String trackingNo) {
        return orderFeignClient.updateOrderStatus(orderNo, 2);
    }

    /**
     * 拆分发货：对勾选的订单明细发货
     */
    @PostMapping("/{orderNo}/ship-items")
    @RequirePerm("m:order:ship")
    public R<Boolean> shipItems(@PathVariable String orderNo,
                                @RequestParam String company,
                                @RequestParam(required = false) String trackingNo,
                                @RequestBody List<Long> itemIds) {
        return orderFeignClient.shipItems(orderNo, company, trackingNo, itemIds);
    }
}
