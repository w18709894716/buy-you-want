package com.byw.shop.feign;

import com.byw.api.shop.ShopFeignClient;
import com.byw.api.shop.dto.MerchantAccountDTO;
import com.byw.api.shop.dto.ShopDTO;
import com.byw.common.core.result.R;
import com.byw.common.security.annotation.Public;
import com.byw.common.security.context.UserContext;
import com.byw.shop.service.ShopService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/feign/shop")
@RequiredArgsConstructor
@Public
public class ShopFeignImpl implements ShopFeignClient {

    private final ShopService shopService;

    @Override
    @GetMapping("/{shopId}")
    public R<ShopDTO> getShopById(@PathVariable("shopId") Long shopId) {
        return R.ok(shopService.getShopById(shopId));
    }

    @Override
    @GetMapping("/batch")
    public R<List<ShopDTO>> getShopsByIds(@RequestParam("ids") List<Long> ids) {
        return R.ok(shopService.getShopsByIds(ids));
    }

    @Override
    @GetMapping("/merchant/username/{username}")
    public R<MerchantAccountDTO> getMerchantByUsername(@PathVariable("username") String username) {
        return R.ok(shopService.getMerchantByUsername(username));
    }

    @Override
    @GetMapping("/merchant/{merchantId}")
    public R<MerchantAccountDTO> getMerchantById(@PathVariable("merchantId") Long merchantId) {
        return R.ok(shopService.getMerchantById(merchantId));
    }

    @Override
    @PutMapping("/update")
    public R<Void> updateShop(@RequestBody ShopDTO shopDTO) {
        // 强制作用于当前登录商家的 shopId（由 BFF 透传身份头重建上下文）
        shopDTO.setId(UserContext.getShopId());
        shopService.updateShop(shopDTO);
        return R.ok();
    }
}
