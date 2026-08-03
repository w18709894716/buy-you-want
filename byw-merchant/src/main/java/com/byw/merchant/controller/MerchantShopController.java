package com.byw.merchant.controller;

import com.byw.api.shop.ShopFeignClient;
import com.byw.api.shop.dto.ShopDTO;
import com.byw.common.core.result.R;
import com.byw.common.security.annotation.RequirePerm;
import com.byw.common.security.context.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 商家端店铺信息：查看/维护当前登录商家的店铺（作用域强制为 UserContext 的 shopId）。
 */
@RestController
@RequestMapping("/merchant/shop")
@RequirePerm("m:shop:info")
@RequiredArgsConstructor
public class MerchantShopController {

    private final ShopFeignClient shopFeignClient;

    @GetMapping("/info")
    public R<ShopDTO> myShop() {
        return shopFeignClient.getShopById(UserContext.getShopId());
    }

    @PutMapping("/info")
    public R<Void> updateShop(@RequestBody ShopDTO shopDTO) {
        // 强制归属当前登录商家店铺，下游再次校验
        shopDTO.setId(UserContext.getShopId());
        return shopFeignClient.updateShop(shopDTO);
    }
}
