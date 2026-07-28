package com.byw.settle.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 余额流水（每笔资金变动一条）。
 */
@Data
@TableName("t_balance_flow")
public class BalanceFlow {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 流水号 */
    private String flowNo;

    /** 归属店铺ID */
    private Long shopId;

    /** 1结算待入账 2结算入账 3提现冻结 4提现成功 5提现驳回解冻 */
    private Integer type;

    /** 变动金额(正入账/负出账) */
    private BigDecimal amount;

    /** 变动后可用余额 */
    private BigDecimal balanceAfter;

    /** 关联单号(结算单号/提现单号) */
    private String refNo;

    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
