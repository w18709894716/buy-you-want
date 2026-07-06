package com.byw.cart.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.byw.api.product.ProductFeignClient;
import com.byw.api.product.dto.SkuDTO;
import com.byw.api.product.dto.ProductDTO;
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

        // 获取商品信息（名称、主图）
        String productName = null;
        String image = sku.getImage();
        try {
            R<ProductDTO> productResult = productFeignClient.getProductById(sku.getProductId());
            if (productResult.isSuccess() && productResult.getData() != null) {
                productName = productResult.getData().getName();
                if (image == null || image.isBlank()) {
                    image = productResult.getData().getMainImage();
                }
            }
        } catch (Exception e) {
            log.warn("获取商品信息失败，productId={}: {}", sku.getProductId(), e.getMessage());
        }

        // 查询购物车中是否已存在该商品（包含已软删除的记录，避免唯一索引冲突）
        CartItem existItem = baseMapper.selectByUserAndSkuIncludeDeleted(userId, skuId);

        if (existItem != null && existItem.getDeleted() == 0) {
            // 未删除：更新数量和价格
            existItem.setQuantity(existItem.getQuantity() + quantity);
            existItem.setPrice(sku.getPrice());
            existItem.setSkuName(sku.getSkuName());
            existItem.setSpecData(sku.getSpecData());
            if (productName != null) existItem.setProductName(productName);
            if (image != null) existItem.setProductImage(image);
            updateById(existItem);
        } else if (existItem != null) {
            // 已软删除：恢复记录并更新
            existItem.setQuantity(quantity);
            existItem.setPrice(sku.getPrice());
            existItem.setSkuName(sku.getSkuName());
            existItem.setProductName(productName);
            existItem.setSpecData(sku.getSpecData());
            existItem.setProductImage(image);
            existItem.setProductId(sku.getProductId());
            baseMapper.restoreById(existItem);
        } else {
            // 不存在则新增
            CartItem cartItem = new CartItem();
            cartItem.setUserId(userId);
            cartItem.setSkuId(skuId);
            cartItem.setProductId(sku.getProductId());
            cartItem.setSkuName(sku.getSkuName());
            cartItem.setProductName(productName);
            cartItem.setSpecData(sku.getSpecData());
            cartItem.setProductImage(image);
            cartItem.setQuantity(quantity);
            cartItem.setPrice(sku.getPrice());
            cartItem.setSelected(1);
            save(cartItem);
        }

        // 更新Redis缓存中的购物车商品数量
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

    @Override
    public void changeSku(Long userId, Long oldSkuId, Long newSkuId) {
        if (oldSkuId.equals(newSkuId)) return;

        // 获取新 SKU 信息
        R<SkuDTO> skuResult = productFeignClient.getSkuById(newSkuId);
        if (!skuResult.isSuccess() || skuResult.getData() == null) {
            throw new BusinessException("商品规格不存在");
        }
        SkuDTO newSku = skuResult.getData();

        // 获取商品信息
        String productName = null;
        String image = newSku.getImage();
        try {
            R<ProductDTO> productResult = productFeignClient.getProductById(newSku.getProductId());
            if (productResult.isSuccess() && productResult.getData() != null) {
                productName = productResult.getData().getName();
                if (image == null || image.isBlank()) {
                    image = productResult.getData().getMainImage();
                }
            }
        } catch (Exception e) {
            log.warn("获取商品信息失败: {}", e.getMessage());
        }

        // 检查新 SKU 是否已在购物车中
        CartItem existNew = baseMapper.selectByUserAndSkuIncludeDeleted(userId, newSkuId);
        CartItem oldItem = getOne(new LambdaQueryWrapper<CartItem>()
                .eq(CartItem::getUserId, userId)
                .eq(CartItem::getSkuId, oldSkuId));

        if (oldItem == null) {
            throw new BusinessException("购物车中不存在该商品");
        }

        if (existNew != null && existNew.getDeleted() == 0) {
            // 新 SKU 已存在：合并数量，删除旧记录
            existNew.setQuantity(existNew.getQuantity() + oldItem.getQuantity());
            updateById(existNew);
            removeById(oldItem.getId());
        } else {
            // 若目标 SKU 有软删除残留，先硬删除避免唯一索引冲突
            if (existNew != null) {
                baseMapper.hardDeleteById(existNew.getId());
            }
            // 直接更新旧记录的 SKU 信息
            oldItem.setSkuId(newSkuId);
            oldItem.setSkuName(newSku.getSkuName());
            oldItem.setSpecData(newSku.getSpecData());
            oldItem.setPrice(newSku.getPrice());
            oldItem.setProductId(newSku.getProductId());
            if (productName != null) oldItem.setProductName(productName);
            if (image != null) oldItem.setProductImage(image);
            updateById(oldItem);
        }
        updateCartCountCache(userId);
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
