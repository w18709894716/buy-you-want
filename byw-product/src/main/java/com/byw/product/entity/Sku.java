package com.byw.product.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("t_sku")
public class Sku implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long productId;

    /** 归属店铺ID（多租户维度，冗余自商品便于按店铺聚合） */
    private Long shopId;

    private String skuCode;

    private String skuName;

    private String specData;

    private BigDecimal price;

    private BigDecimal costPrice;

    private Integer stock;

    private Integer lockStock;

    private String image;

    private BigDecimal weight;

    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
