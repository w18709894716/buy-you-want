package com.byw.api.settle.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 佣金规则（按商品分类配置佣金率；categoryId=0 为平台默认兜底规则）。
 */
@Data
public class CommissionRuleDTO implements Serializable {

    private Long id;

    /** 商品分类ID（0 表示平台默认兜底规则） */
    private Long categoryId;

    /** 分类名称（展示用） */
    private String categoryName;

    /** 佣金率（0~1 的小数，如 0.0500 表示 5%） */
    private BigDecimal commissionRate;

    /** 是否启用 0 停用 1 启用 */
    private Integer enabled;

    private LocalDateTime updatedAt;
}
