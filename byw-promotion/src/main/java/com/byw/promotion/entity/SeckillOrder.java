package com.byw.promotion.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_seckill_order")
public class SeckillOrder {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long activityId;

    private Long itemId;

    private Long userId;

    private String orderNo;

    /** 0待支付 1已支付 2已取消 */
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
