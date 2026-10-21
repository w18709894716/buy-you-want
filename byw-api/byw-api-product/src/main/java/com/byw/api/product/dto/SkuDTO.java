package com.byw.api.product.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
public class SkuDTO implements Serializable {
    private Long id;
    private Long productId;
    private Long shopId;
    @Size(max = 100, message = "规格编码过长")
    private String skuCode;
    @Size(max = 200, message = "规格名称过长")
    private String skuName;
    private String specData;
    @NotNull(message = "请填写规格价格")
    @DecimalMin(value = "0.01", message = "规格价格必须大于 0")
    private BigDecimal price;
    private BigDecimal costPrice;
    @Min(value = 0, message = "库存不能为负数")
    private Integer stock;
    private Integer lockStock;
    @Size(max = 500, message = "规格图片地址过长")
    private String image;
    private Integer status;
}
