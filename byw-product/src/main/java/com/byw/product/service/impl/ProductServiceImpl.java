package com.byw.product.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.byw.common.core.result.PageResult;
import com.byw.product.entity.Product;
import com.byw.product.mapper.ProductMapper;
import com.byw.product.service.ProductService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;

@Service
public class ProductServiceImpl extends ServiceImpl<ProductMapper, Product> implements ProductService {

    @Override
    public PageResult<Product> searchProducts(String keyword, Long categoryId,
                                              BigDecimal minPrice, BigDecimal maxPrice,
                                              String sortBy, Integer pageNum, Integer pageSize) {
        Page<Product> page = new Page<>(pageNum == null ? 1 : pageNum, pageSize == null ? 10 : pageSize);

        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Product::getStatus, 1);

        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(Product::getName, keyword)
                    .or()
                    .like(Product::getSubtitle, keyword));
        }

        if (categoryId != null) {
            wrapper.eq(Product::getCategoryId, categoryId);
        }

        // sort
        if ("sales".equals(sortBy)) {
            wrapper.orderByDesc(Product::getSalesCount);
        } else if ("newest".equals(sortBy)) {
            wrapper.orderByDesc(Product::getCreatedAt);
        } else {
            wrapper.orderByDesc(Product::getCreatedAt);
        }

        Page<Product> result = baseMapper.selectPage(page, wrapper);
        return PageResult.of(result.getRecords(), result.getTotal(),
                (int) result.getCurrent(), (int) result.getSize());
    }

    @Override
    public Product getProductDetail(Long id) {
        return baseMapper.selectById(id);
    }
}
