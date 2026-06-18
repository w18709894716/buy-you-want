package com.byw.promotion.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("t_seckill_activity")
public class SeckillActivity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private Long productId;

    private Long skuId;

    private BigDecimal seckillPrice;

    private Integer totalStock;

    private Integer availableStock;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    /** 0未开始 1进行中 2已结束 */
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
