package com.byw.merchant.controller;

import com.byw.api.shop.ShopFeignClient;
import com.byw.api.shop.dto.ShopDTO;
import com.byw.common.core.result.R;
import com.byw.common.security.context.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 商家端控制台：登录即可访问的公共概览数据（无 @RequirePerm，区别于受 m:shop:info 管控的店铺信息管理）。
 * 与平台端 DashboardController（/admin/dashboard）对称，避免公共首页复用带权限业务接口导致越权拦截。
 */
@RestController
@RequestMapping("/merchant/dashboard")
@RequiredArgsConstructor
public class MerchantDashboardController {

    private final ShopFeignClient shopFeignClient;

    /** 店铺基础信息（控制台展示用，登录即可读） */
    @GetMapping("/overview")
    public R<ShopDTO> overview() {
        return shopFeignClient.getShopById(UserContext.getShopId());
    }
}
