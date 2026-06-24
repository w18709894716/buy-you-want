package com.byw.api.product.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class ProductDTO implements Serializable {
    private Long id;
    private String name;
    private String subtitle;
    private Long categoryId;
    private Long brandId;
    private String mainImage;
    private String subImages;
    private Integer status;
    private Integer salesCount;
    private LocalDateTime createdAt;
}
