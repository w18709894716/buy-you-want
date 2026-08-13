package com.byw.api.shop;

import com.byw.api.shop.dto.MerchantAccountDTO;
import com.byw.api.shop.dto.ShopDTO;
import com.byw.common.core.result.PageResult;
import com.byw.common.core.result.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient(name = "byw-shop", contextId = "shopFeignClient")
public interface ShopFeignClient {

    @GetMapping("/feign/shop/{shopId}")
    R<ShopDTO> getShopById(@PathVariable("shopId") Long shopId);

    @GetMapping("/feign/shop/batch")
    R<List<ShopDTO>> getShopsByIds(@RequestParam("ids") List<Long> ids);

    @GetMapping("/feign/shop/merchant/username/{username}")
    R<MerchantAccountDTO> getMerchantByUsername(@PathVariable("username") String username);

    @GetMapping("/feign/shop/merchant/{merchantId}")
    R<MerchantAccountDTO> getMerchantById(@PathVariable("merchantId") Long merchantId);

    @GetMapping("/feign/shop/merchant/batch")
    R<List<MerchantAccountDTO>> getMerchantsByIds(@RequestParam("ids") List<Long> ids);

    @GetMapping("/feign/shop/merchant/owner")
    R<MerchantAccountDTO> getShopOwner(@RequestParam("shopId") Long shopId);

    @PutMapping("/feign/shop/update")
    R<Void> updateShop(@RequestBody ShopDTO shopDTO);

    // ========== 商家子账号（员工）管理 ==========

    @GetMapping("/feign/shop/staff/page")
    R<PageResult<MerchantAccountDTO>> listStaff(@RequestParam("parentId") Long parentId,
                                                @RequestParam(value = "pageNum", defaultValue = "1") Integer pageNum,
                                                @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize);

    /** 按店铺查启用子账号（供下拉选项等不依赖调用者是否主账号的场景） */
    @GetMapping("/feign/shop/staff/by-shop")
    R<List<MerchantAccountDTO>> listActiveStaffByShop(@RequestParam("shopId") Long shopId,
                                                      @RequestParam(value = "limit", defaultValue = "200") Integer limit);

    @PostMapping("/feign/shop/staff")
    R<Long> createStaff(@RequestParam("parentId") Long parentId,
                        @RequestParam("shopId") Long shopId,
                        @RequestBody MerchantAccountDTO dto);

    @PutMapping("/feign/shop/staff/{staffId}/status")
    R<Void> updateStaffStatus(@PathVariable("staffId") Long staffId,
                              @RequestParam("parentId") Long parentId,
                              @RequestParam("status") Integer status);

    @PutMapping("/feign/shop/staff/{staffId}/password")
    R<Void> resetStaffPassword(@PathVariable("staffId") Long staffId,
                               @RequestParam("parentId") Long parentId,
                               @RequestParam("password") String password);
}
