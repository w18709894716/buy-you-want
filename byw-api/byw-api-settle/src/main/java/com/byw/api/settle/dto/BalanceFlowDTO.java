package com.byw.api.settle.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 余额流水（每笔资金变动一条）。
 */
@Data
public class BalanceFlowDTO implements Serializable {

    private Long id;

    private String flowNo;

    private Long shopId;

    /**
     * 流水类型：
     * 1 结算待入账(计入待结算) 2 结算入账(转可用) 3 提现冻结 4 提现成功 5 提现驳回解冻
     */
    private Integer type;

    /** 类型描述（后端回填便于展示） */
    private String typeDesc;

    /** 变动金额（正数入账/正向，负数出账/扣减） */
    private BigDecimal amount;

    /** 变动后可用余额 */
    private BigDecimal balanceAfter;

    /** 关联单号（结算单号/提现单号） */
    private String refNo;

    private String remark;

    private LocalDateTime createdAt;
}
