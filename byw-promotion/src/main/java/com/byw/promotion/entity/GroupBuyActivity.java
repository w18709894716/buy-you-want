package com.byw.promotion.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("t_group_buy_activity")
public class GroupBuyActivity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private Long productId;

    private BigDecimal groupPrice;

    private BigDecimal originalPrice;

    private Integer groupSize;

    private Integer maxGroups;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
