package com.byw.cart.feign;

import com.byw.api.cart.CartFeignClient;
import com.byw.api.cart.dto.CartItemDTO;
import com.byw.cart.entity.CartItem;
import com.byw.cart.service.CartService;
import com.byw.common.core.result.R;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/feign/cart")
@RequiredArgsConstructor
public class CartFeignImpl implements CartFeignClient {

    private final CartService cartService;

    @Override
    @PostMapping("/clear")
    public R<Boolean> clearCartItems(@RequestParam("userId") Long userId, @RequestBody List<Long> skuIds) {
        if (skuIds == null || skuIds.isEmpty()) {
            cartService.clearCart(userId);
        } else {
            skuIds.forEach(skuId -> cartService.removeItem(userId, skuId));
        }
        return R.ok(true);
    }

    @Override
    @GetMapping("/{userId}")
    public R<List<CartItemDTO>> getCartItems(@PathVariable("userId") Long userId) {
        List<CartItem> cartItems = cartService.getCartItems(userId);
        List<CartItemDTO> dtoList = cartItems.stream().map(item -> {
            CartItemDTO dto = new CartItemDTO();
            BeanUtils.copyProperties(item, dto);
            return dto;
        }).collect(Collectors.toList());
        return R.ok(dtoList);
    }
}
