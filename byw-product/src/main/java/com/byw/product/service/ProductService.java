package com.byw.product.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.byw.common.core.result.PageResult;
import com.byw.product.entity.Product;

public interface ProductService extends IService<Product> {

    PageResult<Product> searchProducts(String keyword, Long categoryId,
                                       java.math.BigDecimal minPrice, java.math.BigDecimal maxPrice,
                                       String sortBy, Integer pageNum, Integer pageSize);

    /** ES 关键词匹配商品 id；ES 不可用时返回 null（调用方回退 MySQL LIKE） */
    java.util.List<Long> matchProductIdsByEs(String keyword);

    /** 价格筛选/排序场景：SQL 聚合 JOIN + DB 分页，避免全量商品加载进内存 */
    com.baomidou.mybatisplus.core.metadata.IPage<com.byw.product.entity.ProductWithPrice> pageWithMinPrice(
            com.baomidou.mybatisplus.core.metadata.IPage<com.byw.product.entity.ProductWithPrice> page,
            Long shopId, Long brandId, java.util.List<Long> categoryIds, java.util.List<Long> productIds,
            String keyword, java.math.BigDecimal minPrice, java.math.BigDecimal maxPrice, String orderBy);

    /**
     * 商品详情读缓存，仅供展示类只读链路使用。
     * 写链路（改状态/审核/删除前的读取）必须走 getById，否则会读到旧数据。
     */
    Product getProductDetail(Long id);
}
