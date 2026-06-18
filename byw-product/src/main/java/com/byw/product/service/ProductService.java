package com.byw.product.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.byw.common.core.result.PageResult;
import com.byw.product.entity.Product;

public interface ProductService extends IService<Product> {

    PageResult<Product> searchProducts(String keyword, Long categoryId,
                                       java.math.BigDecimal minPrice, java.math.BigDecimal maxPrice,
                                       String sortBy, Integer pageNum, Integer pageSize);

    Product getProductDetail(Long id);
}
