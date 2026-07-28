package com.byw.api.shop;

import com.byw.api.shop.dto.MerchantAccountDTO;
import com.byw.api.shop.dto.ShopDTO;
import com.byw.common.core.result.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

@FeignClient(name = "byw-shop", contextId = "shopFeignClient")
public interface ShopFeignClient {

    @GetMapping("/feign/shop/{shopId}")
    R<ShopDTO> getShopById(@PathVariable("shopId") Long shopId);

    @GetMapping("/feign/shop/batch")
    R<List<ShopDTO>> getShopsByIds(@org.springframework.web.bind.annotation.RequestParam("ids") List<Long> ids);

    @GetMapping("/feign/shop/merchant/username/{username}")
    R<MerchantAccountDTO> getMerchantByUsername(@PathVariable("username") String username);

    @GetMapping("/feign/shop/merchant/{merchantId}")
    R<MerchantAccountDTO> getMerchantById(@PathVariable("merchantId") Long merchantId);

    @PutMapping("/feign/shop/update")
    R<Void> updateShop(@RequestBody ShopDTO shopDTO);
}
