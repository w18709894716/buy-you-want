package com.byw.cart.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.byw.cart.entity.CartItem;

import java.util.List;

public interface CartService extends IService<CartItem> {

    /**
     * 添加商品到购物车
     */
    void addToCart(Long userId, Long skuId, Integer quantity);

    /**
     * 更新购物车商品数量
     */
    void updateQuantity(Long userId, Long skuId, Integer quantity);

    /**
     * 移除购物车商品
     */
    void removeItem(Long userId, Long skuId);

    /**
     * 清空购物车
     */
    void clearCart(Long userId);

    /**
     * 获取购物车列表
     */
    List<CartItem> getCartItems(Long userId);

    /**
     * 更新商品选中状态
     */
    void updateSelected(Long userId, Long skuId, Integer selected);

    /**
     * 全选/取消全选
     */
    void selectAll(Long userId, Integer selected);

    /**
     * 购物车内切换规格（更换 SKU）
     */
    void changeSku(Long userId, Long oldSkuId, Long newSkuId);
}
