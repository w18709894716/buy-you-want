package com.byw.promotion.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("t_seckill_activity_item")
public class SeckillActivityItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long activityId;

    private Long productId;

    private Long skuId;

    private BigDecimal seckillPrice;

    private Integer totalStock;

    private Integer availableStock;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
