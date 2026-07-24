package com.byw.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.byw.api.product.ProductFeignClient;
import com.byw.api.product.dto.ProductDTO;
import com.byw.api.product.dto.SkuDTO;
import com.byw.common.core.result.PageResult;
import com.byw.common.core.result.R;
import com.byw.user.entity.UserFavorite;
import com.byw.user.mapper.UserFavoriteMapper;
import com.byw.user.service.UserFavoriteService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserFavoriteServiceImpl implements UserFavoriteService {

    private final UserFavoriteMapper userFavoriteMapper;
    private final ProductFeignClient productFeignClient;

    @Override
    public void addFavorite(Long userId, Long productId) {
        UserFavorite exist = userFavoriteMapper.selectByUserAndProductIncludeDeleted(userId, productId);
        if (exist != null && (exist.getDeleted() == null || exist.getDeleted() == 0)) {
            // 已收藏，幂等返回
            return;
        }
        if (exist != null) {
            // 曾收藏后取消，恢复记录
            userFavoriteMapper.restoreById(exist.getId());
            return;
        }
        UserFavorite favorite = new UserFavorite();
        favorite.setUserId(userId);
        favorite.setProductId(productId);
        userFavoriteMapper.insert(favorite);
    }

    @Override
    public void removeFavorite(Long userId, Long productId) {
        userFavoriteMapper.delete(new LambdaQueryWrapper<UserFavorite>()
                .eq(UserFavorite::getUserId, userId)
                .eq(UserFavorite::getProductId, productId));
    }

    @Override
    public boolean isFavorite(Long userId, Long productId) {
        return userFavoriteMapper.selectCount(new LambdaQueryWrapper<UserFavorite>()
                .eq(UserFavorite::getUserId, userId)
                .eq(UserFavorite::getProductId, productId)) > 0;
    }

    @Override
    public List<Long> getFavoriteProductIds(Long userId) {
        return userFavoriteMapper.selectList(new LambdaQueryWrapper<UserFavorite>()
                        .eq(UserFavorite::getUserId, userId)
                        .orderByDesc(UserFavorite::getCreatedAt))
                .stream()
                .map(UserFavorite::getProductId)
                .collect(Collectors.toList());
    }

    @Override
    public PageResult<Map<String, Object>> getFavorites(Long userId, Integer pageNum, Integer pageSize) {
        int pn = pageNum == null ? 1 : pageNum;
        int ps = pageSize == null ? 10 : pageSize;
        Page<UserFavorite> page = new Page<>(pn, ps);
        Page<UserFavorite> favoritePage = userFavoriteMapper.selectPage(page,
                new LambdaQueryWrapper<UserFavorite>()
                        .eq(UserFavorite::getUserId, userId)
                        .orderByDesc(UserFavorite::getCreatedAt));

        List<Map<String, Object>> list = favoritePage.getRecords().stream()
                .map(this::buildFavoriteMap)
                .collect(Collectors.toList());

        return PageResult.of(list, favoritePage.getTotal(), pn, ps);
    }

    private Map<String, Object> buildFavoriteMap(UserFavorite favorite) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("favoriteId", favorite.getId());
        map.put("productId", favorite.getProductId());
        map.put("createdAt", favorite.getCreatedAt());

        ProductDTO product = resolveProduct(favorite.getProductId());
        if (product == null) {
            map.put("productName", "商品已下架");
            map.put("image", null);
            map.put("price", null);
            map.put("salesCount", 0);
            map.put("available", false);
            return map;
        }
        map.put("productName", product.getName());
        map.put("image", product.getMainImage());
        map.put("price", resolveMinPrice(product));
        map.put("salesCount", product.getSalesCount() != null ? product.getSalesCount() : 0);
        // status==1 且商品存在时视为可购买
        map.put("available", product.getStatus() != null && product.getStatus() == 1);
        return map;
    }

    private ProductDTO resolveProduct(Long productId) {
        if (productId == null) return null;
        try {
            R<ProductDTO> resp = productFeignClient.getProductById(productId);
            if (resp != null && resp.getData() != null) {
                return resp.getData();
            }
        } catch (Exception e) {
            log.warn("获取收藏商品信息失败: productId={}, error={}", productId, e.getMessage());
        }
        return null;
    }

    private BigDecimal resolveMinPrice(ProductDTO product) {
        List<SkuDTO> skus = product.getSkus();
        if (skus == null || skus.isEmpty()) {
            return product.getMinPrice();
        }
        return skus.stream()
                .map(SkuDTO::getPrice)
                .filter(Objects::nonNull)
                .min(Comparator.naturalOrder())
                .orElse(product.getMinPrice());
    }
}
