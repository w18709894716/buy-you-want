package com.byw.api.settle.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 商家余额账户。
 */
@Data
public class ShopBalanceDTO implements Serializable {

    private Long id;

    /** 归属店铺ID */
    private Long shopId;

    /** 累计确认收入（收货结算计入） */
    private BigDecimal totalIncome;

    /** 待结算金额（收货后处于售后冷静期，尚未入账可用） */
    private BigDecimal pendingAmount;

    /** 可提现余额 */
    private BigDecimal availableBalance;

    /** 提现冻结中金额（提现申请待审核） */
    private BigDecimal frozenAmount;

    /** 累计已提现金额 */
    private BigDecimal withdrawnAmount;

    private LocalDateTime updatedAt;
}
