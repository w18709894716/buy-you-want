package com.byw.promotion.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("t_coupon")
public class Coupon {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    /** 1满减券 2折扣券 3无门槛券 */
    private Integer type;

    private BigDecimal discountValue;

    private BigDecimal minAmount;

    private Integer totalCount;

    private Integer claimedCount;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private Integer status;

    /** 是否新人专享：0普通 1新人专享 */
    private Integer newUser;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
