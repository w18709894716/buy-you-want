package com.byw.api.product.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class ProductDTO implements Serializable {
    private Long id;
    private String name;
    private String subtitle;
    private Long categoryId;
    private Long brandId;
    private String mainImage;
    private String subImages;
    private String detailHtml;
    private Integer status;
    private Integer salesCount;
    private BigDecimal minPrice;
    /** 该商品所有 SKU 库存之和 */
    private Integer totalStock;
    private LocalDateTime createdAt;
    private List<SkuDTO> skus;
}
