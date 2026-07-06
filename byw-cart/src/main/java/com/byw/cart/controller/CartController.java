package com.byw.cart.controller;

import com.byw.cart.entity.CartItem;
import com.byw.cart.service.CartService;
import com.byw.common.core.exception.BusinessException;
import com.byw.common.core.result.R;
import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.byw.common.security.annotation.RequireLogin;
import com.byw.common.security.context.UserContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "购物车", description = "购物车管理")
@Slf4j
@RestController
@RequestMapping("/cart")
@RequiredArgsConstructor
@RequireLogin
public class CartController {

    private final CartService cartService;

    @Operation(summary = "获取购物车列表")
    @SentinelResource(value = "cart:list", fallback = "cartListFallback")
    @GetMapping("/list")
    public R<List<CartItem>> list() {
        Long userId = UserContext.getUserId();
        return R.ok(cartService.getCartItems(userId));
    }

    @Operation(summary = "添加商品到购物车")
    @SentinelResource(value = "cart:add", fallback = "cartAddFallback")
    @PostMapping("/add")
    public R<Void> add(@RequestParam Long skuId, @RequestParam(defaultValue = "1") Integer quantity) {
        Long userId = UserContext.getUserId();
        cartService.addToCart(userId, skuId, quantity);
        return R.ok();
    }

    @Operation(summary = "更新购物车商品数量")
    @PutMapping("/update")
    public R<Void> update(@RequestParam Long skuId, @RequestParam Integer quantity) {
        Long userId = UserContext.getUserId();
        cartService.updateQuantity(userId, skuId, quantity);
        return R.ok();
    }

    @Operation(summary = "移除购物车商品")
    @DeleteMapping("/{skuId}")
    public R<Void> remove(@PathVariable Long skuId) {
        Long userId = UserContext.getUserId();
        cartService.removeItem(userId, skuId);
        return R.ok();
    }

    @Operation(summary = "清空购物车")
    @DeleteMapping("/clear")
    public R<Void> clear() {
        Long userId = UserContext.getUserId();
        cartService.clearCart(userId);
        return R.ok();
    }

    @Operation(summary = "更新商品选中状态")
    @PutMapping("/select/{skuId}")
    public R<Void> select(@PathVariable Long skuId, @RequestParam Integer selected) {
        Long userId = UserContext.getUserId();
        cartService.updateSelected(userId, skuId, selected);
        return R.ok();
    }

    @Operation(summary = "全选/取消全选")
    @PutMapping("/select-all")
    public R<Void> selectAll(@RequestParam Integer selected) {
        Long userId = UserContext.getUserId();
        cartService.selectAll(userId, selected);
        return R.ok();
    }

    @Operation(summary = "购物车内切换规格")
    @PutMapping("/change-sku")
    public R<Void> changeSku(@RequestParam Long oldSkuId, @RequestParam Long newSkuId) {
        Long userId = UserContext.getUserId();
        cartService.changeSku(userId, oldSkuId, newSkuId);
        return R.ok();
    }

    // ========== Sentinel fallback ==========
    private R<List<CartItem>> cartListFallback(Throwable ex) {
        log.error("[cart:list] 触发 fallback，异常: {}", ex.getMessage(), ex);
        if (ex instanceof BusinessException) {
            return R.fail(ex.getMessage());
        }
        return R.fail("系统繁忙，请稍后再试");
    }
    private R<Void> cartAddFallback(Long skuId, Integer quantity, Throwable ex) {
        log.error("[cart:add] 触发 fallback，skuId={}, 异常: {}", skuId, ex.getMessage(), ex);
        if (ex instanceof BusinessException) {
            return R.fail(ex.getMessage());
        }
        return R.fail("系统繁忙，请稍后再试");
    }
}
