package com.byw.product.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 商品 + SKU 最低价（价格筛选/排序分页 JOIN 查询的结果载体，避免全量加载进内存）
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ProductWithPrice extends Product {

    /** 该商品 SKU 最低价（无 SKU 时为 null） */
    private BigDecimal minPrice;
}
