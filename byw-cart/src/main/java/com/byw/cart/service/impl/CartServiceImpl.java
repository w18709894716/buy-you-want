package com.byw.cart.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.byw.api.product.ProductFeignClient;
import com.byw.api.product.dto.SkuDTO;
import com.byw.cart.entity.CartItem;
import com.byw.cart.mapper.CartItemMapper;
import com.byw.cart.service.CartService;
import com.byw.common.core.exception.BusinessException;
import com.byw.common.core.result.R;
import com.byw.common.redis.util.RedisUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class CartServiceImpl extends ServiceImpl<CartItemMapper, CartItem> implements CartService {

    private final ProductFeignClient productFeignClient;
    private final RedisUtil redisUtil;

    private static final String CART_COUNT_KEY = "cart:count:";

    @Override
    public void addToCart(Long userId, Long skuId, Integer quantity) {
        // 调用商品服务获取最新SKU信息
        R<SkuDTO> skuResult = productFeignClient.getSkuById(skuId);
        if (!skuResult.isSuccess() || skuResult.getData() == null) {
            throw new BusinessException("商品不存在");
        }
        SkuDTO sku = skuResult.getData();

        // 查询购物车中是否已存在该商品
        CartItem existItem = getOne(new LambdaQueryWrapper<CartItem>()
                .eq(CartItem::getUserId, userId)
                .eq(CartItem::getSkuId, skuId));

        if (existItem != null) {
            // 已存在则更新数量和价格
            existItem.setQuantity(existItem.getQuantity() + quantity);
            existItem.setPrice(sku.getPrice());
            existItem.setProductImage(sku.getImage());
            existItem.setSkuName(sku.getSkuName());
            updateById(existItem);
        } else {
            // 不存在则新增
            CartItem cartItem = new CartItem();
            cartItem.setUserId(userId);
            cartItem.setSkuId(skuId);
            cartItem.setProductId(sku.getProductId());
            cartItem.setSkuName(sku.getSkuName());
            cartItem.setProductImage(sku.getImage());
            cartItem.setQuantity(quantity);
            cartItem.setPrice(sku.getPrice());
            cartItem.setSelected(1);
            save(cartItem);
        }

        // 更新Redis缓存中的购物车数量
        updateCartCountCache(userId);
    }

    @Override
    public void updateQuantity(Long userId, Long skuId, Integer quantity) {
        if (quantity <= 0) {
            removeItem(userId, skuId);
            return;
        }
        update(new LambdaUpdateWrapper<CartItem>()
                .eq(CartItem::getUserId, userId)
                .eq(CartItem::getSkuId, skuId)
                .set(CartItem::getQuantity, quantity));
    }

    @Override
    public void removeItem(Long userId, Long skuId) {
        remove(new LambdaQueryWrapper<CartItem>()
                .eq(CartItem::getUserId, userId)
                .eq(CartItem::getSkuId, skuId));
        updateCartCountCache(userId);
    }

    @Override
    public void clearCart(Long userId) {
        remove(new LambdaQueryWrapper<CartItem>()
                .eq(CartItem::getUserId, userId));
        redisUtil.set(CART_COUNT_KEY + userId, 0, 30, TimeUnit.DAYS);
    }

    @Override
    public List<CartItem> getCartItems(Long userId) {
        return list(new LambdaQueryWrapper<CartItem>()
                .eq(CartItem::getUserId, userId)
                .orderByDesc(CartItem::getCreatedAt));
    }

    @Override
    public void updateSelected(Long userId, Long skuId, Integer selected) {
        update(new LambdaUpdateWrapper<CartItem>()
                .eq(CartItem::getUserId, userId)
                .eq(CartItem::getSkuId, skuId)
                .set(CartItem::getSelected, selected));
    }

    @Override
    public void selectAll(Long userId, Integer selected) {
        update(new LambdaUpdateWrapper<CartItem>()
                .eq(CartItem::getUserId, userId)
                .set(CartItem::getSelected, selected));
    }

    /**
     * 更新Redis中购物车商品数量缓存
     */
    private void updateCartCountCache(Long userId) {
        long count = count(new LambdaQueryWrapper<CartItem>()
                .eq(CartItem::getUserId, userId));
        redisUtil.set(CART_COUNT_KEY + userId, count, 30, TimeUnit.DAYS);
    }
}
