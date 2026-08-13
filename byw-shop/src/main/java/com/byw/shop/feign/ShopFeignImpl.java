package com.byw.shop.feign;

import com.byw.api.shop.ShopFeignClient;
import com.byw.api.shop.dto.MerchantAccountDTO;
import com.byw.api.shop.dto.ShopDTO;
import com.byw.common.core.result.PageResult;
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
    @GetMapping("/merchant/batch")
    public R<List<MerchantAccountDTO>> getMerchantsByIds(@RequestParam("ids") List<Long> ids) {
        return R.ok(shopService.getMerchantsByIds(ids));
    }

    @Override
    @GetMapping("/merchant/owner")
    public R<MerchantAccountDTO> getShopOwner(@RequestParam("shopId") Long shopId) {
        return R.ok(shopService.getShopOwner(shopId));
    }

    @Override
    @PutMapping("/update")
    public R<Void> updateShop(@RequestBody ShopDTO shopDTO) {
        // 强制作用于当前登录商家的 shopId（由 BFF 透传身份头重建上下文）
        shopDTO.setId(UserContext.getShopId());
        shopService.updateShop(shopDTO);
        return R.ok();
    }

    @Override
    @GetMapping("/staff/page")
    public R<PageResult<MerchantAccountDTO>> listStaff(@RequestParam("parentId") Long parentId,
                                                       @RequestParam(value = "pageNum", defaultValue = "1") Integer pageNum,
                                                       @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize) {
        return R.ok(shopService.listStaff(parentId, pageNum, pageSize));
    }

    @Override
    @GetMapping("/staff/by-shop")
    public R<List<MerchantAccountDTO>> listActiveStaffByShop(@RequestParam("shopId") Long shopId,
                                                             @RequestParam(value = "limit", defaultValue = "200") Integer limit) {
        return R.ok(shopService.listActiveStaffByShop(shopId, limit));
    }

    @Override
    @PostMapping("/staff")
    public R<Long> createStaff(@RequestParam("parentId") Long parentId,
                               @RequestParam("shopId") Long shopId,
                               @RequestBody MerchantAccountDTO dto) {
        return R.ok(shopService.createStaff(parentId, shopId, dto));
    }

    @Override
    @PutMapping("/staff/{staffId}/status")
    public R<Void> updateStaffStatus(@PathVariable("staffId") Long staffId,
                                     @RequestParam("parentId") Long parentId,
                                     @RequestParam("status") Integer status) {
        shopService.updateStaffStatus(parentId, staffId, status);
        return R.ok();
    }

    @Override
    @PutMapping("/staff/{staffId}/password")
    public R<Void> resetStaffPassword(@PathVariable("staffId") Long staffId,
                                      @RequestParam("parentId") Long parentId,
                                      @RequestParam("password") String password) {
        shopService.resetStaffPassword(parentId, staffId, password);
        return R.ok();
    }
}
