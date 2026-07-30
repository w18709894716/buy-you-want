package com.byw.order.feign;

import com.byw.api.order.AfterSaleFeignClient;
import com.byw.api.order.dto.AfterSaleDTO;
import com.byw.common.core.result.PageResult;
import com.byw.common.core.result.R;
import com.byw.common.security.annotation.Public;
import com.byw.order.entity.AfterSale;
import com.byw.order.service.AfterSaleService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 售后审核 Feign 实现（商家侧调用，按 X-Shop-Id 过滤/校验本店，防越权）
 */
@RestController
@RequestMapping("/feign/order/aftersale")
@RequiredArgsConstructor
@Public
public class AfterSaleFeignImpl implements AfterSaleFeignClient {

    private final AfterSaleService afterSaleService;

    @Override
    @GetMapping("/list")
    public R<PageResult<AfterSaleDTO>> listShopAfterSales(@RequestParam(value = "pageNum", defaultValue = "1") Integer pageNum,
                                                          @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize,
                                                          @RequestParam(value = "status", required = false) Integer status) {
        PageResult<AfterSale> page = afterSaleService.getShopAfterSales(status, pageNum, pageSize);
        List<AfterSaleDTO> list = page.getList().stream().map(as -> {
            AfterSaleDTO dto = new AfterSaleDTO();
            BeanUtils.copyProperties(as, dto);
            return dto;
        }).collect(Collectors.toList());
        return R.ok(PageResult.of(list, page.getTotal(), pageNum, pageSize));
    }

    @Override
    @PostMapping("/approve")
    public R<Boolean> approveAfterSale(@RequestParam("id") Long id) {
        afterSaleService.approve(id);
        return R.ok(true);
    }

    @Override
    @PostMapping("/reject")
    public R<Boolean> rejectAfterSale(@RequestParam("id") Long id, @RequestParam("reason") String reason) {
        afterSaleService.reject(id, reason);
        return R.ok(true);
    }

    @Override
    @PostMapping("/confirm-return")
    public R<Boolean> confirmReturnReceived(@RequestParam("id") Long id) {
        afterSaleService.confirmReturnReceived(id);
        return R.ok(true);
    }
}
