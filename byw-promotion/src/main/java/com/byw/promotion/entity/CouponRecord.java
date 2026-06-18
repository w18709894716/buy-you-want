package com.byw.promotion.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_coupon_record")
public class CouponRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long couponId;

    private Long userId;

    private String orderNo;

    /** 0未使用 1已使用 2已过期 */
    private Integer status;

    private LocalDateTime usedTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
