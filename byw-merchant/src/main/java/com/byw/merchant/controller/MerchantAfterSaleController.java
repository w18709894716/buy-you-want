package com.byw.merchant.controller;

import com.byw.api.order.AfterSaleFeignClient;
import com.byw.api.order.dto.AfterSaleDTO;
import com.byw.common.core.constant.CommonConstants;
import com.byw.common.core.result.PageResult;
import com.byw.common.core.result.R;
import com.byw.common.security.annotation.RequireRole;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 商家端售后审核：仅本店售后单（下游按 X-Shop-Id 过滤/校验防越权）。
 */
@RestController
@RequestMapping("/merchant/after-sale")
@RequireRole({CommonConstants.ROLE_MERCHANT_OWNER, CommonConstants.ROLE_MERCHANT_STAFF})
@RequiredArgsConstructor
public class MerchantAfterSaleController {

    private final AfterSaleFeignClient afterSaleFeignClient;

    @GetMapping("/list")
    public R<PageResult<AfterSaleDTO>> list(@RequestParam(defaultValue = "1") Integer pageNum,
                                            @RequestParam(defaultValue = "10") Integer pageSize,
                                            @RequestParam(required = false) Integer status) {
        return afterSaleFeignClient.listShopAfterSales(pageNum, pageSize, status);
    }

    @PostMapping("/{id}/approve")
    public R<Boolean> approve(@PathVariable Long id) {
        return afterSaleFeignClient.approveAfterSale(id);
    }

    @PostMapping("/{id}/reject")
    public R<Boolean> reject(@PathVariable Long id, @RequestParam String reason) {
        return afterSaleFeignClient.rejectAfterSale(id, reason);
    }

    @PostMapping("/{id}/confirm-return")
    public R<Boolean> confirmReturn(@PathVariable Long id) {
        return afterSaleFeignClient.confirmReturnReceived(id);
    }
}
