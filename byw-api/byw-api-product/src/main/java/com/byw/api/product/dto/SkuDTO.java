package com.byw.api.product.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
public class SkuDTO implements Serializable {
    private Long id;
    private Long productId;
    private String skuCode;
    private String skuName;
    private String specData;
    private BigDecimal price;
    private BigDecimal costPrice;
    private Integer stock;
    private Integer lockStock;
    private String image;
    private Integer status;
}
