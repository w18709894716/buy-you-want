package com.byw.settle.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 佣金规则（按商品分类配置佣金率；category_id=0 为平台默认兜底规则）。
 */
@Data
@TableName("t_commission_rule")
public class CommissionRule {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 商品分类ID(0=平台默认兜底规则) */
    private Long categoryId;

    private String categoryName;

    /** 佣金率(0~1小数,如0.0500=5%) */
    private BigDecimal commissionRate;

    /** 0停用 1启用 */
    private Integer enabled;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
