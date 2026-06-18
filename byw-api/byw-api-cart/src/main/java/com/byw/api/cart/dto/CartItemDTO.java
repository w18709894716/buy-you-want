package com.byw.api.cart.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
public class CartItemDTO implements Serializable {
    private Long id;
    private Long userId;
    private Long skuId;
    private Long productId;
    private String skuName;
    private String productImage;
    private Integer quantity;
    private BigDecimal price;
    private Integer selected;
}
