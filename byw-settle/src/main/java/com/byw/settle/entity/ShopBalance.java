package com.byw.settle.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 商家余额账户（每店铺一条）。
 */
@Data
@TableName("t_shop_balance")
public class ShopBalance {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 归属店铺ID */
    private Long shopId;

    /** 累计确认收入 */
    private BigDecimal totalIncome;

    /** 待结算金额(冷静期) */
    private BigDecimal pendingAmount;

    /** 可提现余额 */
    private BigDecimal availableBalance;

    /** 提现冻结中金额 */
    private BigDecimal frozenAmount;

    /** 累计已提现 */
    private BigDecimal withdrawnAmount;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
