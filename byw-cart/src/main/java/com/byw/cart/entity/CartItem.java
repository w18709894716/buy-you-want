package com.byw.cart.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("t_cart_item")
public class CartItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** 归属店铺ID（用于购物车按店铺分组） */
    private Long shopId;

    private Long skuId;

    private Long productId;

    private String skuName;

    private String productName;

    private String specData;

    private String productImage;

    private Integer quantity;

    private BigDecimal price;

    private Integer selected;

    /** 店铺名称（购物车分组展示用，非持久化字段） */
    @TableField(exist = false)
    private String shopName;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
