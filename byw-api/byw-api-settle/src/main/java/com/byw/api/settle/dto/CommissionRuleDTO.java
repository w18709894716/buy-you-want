package com.byw.api.settle.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
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
    @NotNull(message = "分类ID不能为空（0 表示平台默认兜底规则）")
    @Min(value = 0, message = "分类ID不能为负")
    private Long categoryId;

    /** 分类名称（展示用） */
    private String categoryName;

    /** 佣金率（0~1 的小数，如 0.0500 表示 5%） */
    @NotNull(message = "请填写佣金率")
    @DecimalMin(value = "0", message = "佣金率不能为负")
    @DecimalMax(value = "1", message = "佣金率不能大于 1（即 100%）")
    private BigDecimal commissionRate;

    /** 是否启用 0 停用 1 启用 */
    private Integer enabled;

    private LocalDateTime updatedAt;
}
