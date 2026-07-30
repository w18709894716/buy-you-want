package com.byw.api.order;

import com.byw.api.order.dto.AfterSaleDTO;
import com.byw.common.core.result.PageResult;
import com.byw.common.core.result.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

/**
 * 售后审核 Feign 契约（商家侧调用，下游按 X-Shop-Id 过滤/校验本店）
 */
@FeignClient(name = "byw-order", contextId = "afterSaleFeignClient")
public interface AfterSaleFeignClient {

    @GetMapping("/feign/order/aftersale/list")
    R<PageResult<AfterSaleDTO>> listShopAfterSales(@RequestParam(value = "pageNum", defaultValue = "1") Integer pageNum,
                                                   @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize,
                                                   @RequestParam(value = "status", required = false) Integer status);

    @PostMapping("/feign/order/aftersale/approve")
    R<Boolean> approveAfterSale(@RequestParam("id") Long id);

    @PostMapping("/feign/order/aftersale/reject")
    R<Boolean> rejectAfterSale(@RequestParam("id") Long id, @RequestParam("reason") String reason);

    @PostMapping("/feign/order/aftersale/confirm-return")
    R<Boolean> confirmReturnReceived(@RequestParam("id") Long id);
}
