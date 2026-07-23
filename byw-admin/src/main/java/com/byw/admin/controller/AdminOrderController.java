package com.byw.admin.controller;

import com.byw.api.order.OrderFeignClient;
import com.byw.api.order.dto.OrderDetailDTO;
import com.byw.common.core.result.PageResult;
import com.byw.common.core.result.R;
import com.byw.common.security.annotation.RequireAdmin;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/order")
@RequireAdmin
@RequiredArgsConstructor
public class AdminOrderController {

    private final OrderFeignClient orderFeignClient;

    @GetMapping("/list")
    public R<PageResult<OrderDetailDTO>> list(@RequestParam(defaultValue = "1") Integer pageNum,
                                              @RequestParam(defaultValue = "10") Integer pageSize,
                                              @RequestParam(required = false) java.util.List<Integer> status,
                                              @RequestParam(required = false) String orderNo) {
        return orderFeignClient.listOrders(pageNum, pageSize, status, orderNo);
    }

    @GetMapping("/{orderNo}")
    public R<OrderDetailDTO> getOrderDetail(@PathVariable String orderNo) {
        return orderFeignClient.getOrderDetail(orderNo);
    }

    /**
     * 订单发货（整单，兼容旧接口）
     */
    @PutMapping("/{orderNo}/ship")
    public R<Boolean> ship(@PathVariable String orderNo,
                           @RequestParam(required = false) String company,
                           @RequestParam(required = false) String trackingNo) {
        // 更新订单状态为已发货 (status=2)
        // TODO: 同时创建物流记录（company, trackingNo）
        return orderFeignClient.updateOrderStatus(orderNo, 2);
    }

    /**
     * 拆分发货：对勾选的订单明细发货
     */
    @PostMapping("/{orderNo}/ship-items")
    public R<Boolean> shipItems(@PathVariable String orderNo,
                                @RequestParam String company,
                                @RequestParam(required = false) String trackingNo,
                                @RequestBody java.util.List<Long> itemIds) {
        return orderFeignClient.shipItems(orderNo, company, trackingNo, itemIds);
    }
}
