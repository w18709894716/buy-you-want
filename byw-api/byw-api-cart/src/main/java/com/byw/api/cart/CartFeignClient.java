package com.byw.api.cart;

import com.byw.api.cart.dto.CartItemDTO;
import com.byw.common.core.result.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@FeignClient(name = "byw-cart", contextId = "cartFeignClient")
public interface CartFeignClient {

    @PostMapping("/feign/cart/clear")
    R<Boolean> clearCartItems(@RequestParam("userId") Long userId, @RequestBody List<Long> skuIds);

    @GetMapping("/feign/cart/{userId}")
    R<List<CartItemDTO>> getCartItems(@PathVariable("userId") Long userId);
}
