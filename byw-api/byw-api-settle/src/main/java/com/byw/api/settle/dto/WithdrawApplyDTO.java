package com.byw.api.settle.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 商家提现申请入参。
 */
@Data
public class WithdrawApplyDTO implements Serializable {

    /** 提现金额 */
    private BigDecimal amount;

    /** 收款账户类型：bank / alipay / wechat */
    private String accountType;

    /** 收款账号 */
    private String accountNo;

    /** 收款人姓名 */
    private String accountName;
}
