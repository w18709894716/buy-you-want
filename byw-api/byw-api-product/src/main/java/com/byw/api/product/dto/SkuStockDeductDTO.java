package com.byw.api.product.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SkuStockDeductDTO implements Serializable {
    private Long skuId;
    private Integer quantity;
}
