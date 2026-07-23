package com.byw.order.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("t_order_item")
public class OrderItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long orderId;

    private String orderNo;

    private Long userId;

    private Long productId;

    private Long skuId;

    private String productName;

    private String skuName;

    private String productImage;

    private BigDecimal price;

    private Integer quantity;

    private BigDecimal subtotal;

    /** 发货状态 0未发货 1已发货 */
    private Integer shipStatus;

    /** 运单号 */
    private String trackingNo;

    /** 物流公司 */
    private String companyName;

    /** 发货时间 */
    private LocalDateTime shipTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
