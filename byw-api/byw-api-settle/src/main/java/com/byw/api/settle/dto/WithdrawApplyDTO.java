package com.byw.api.settle.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 商家提现申请入参。
 */
@Data
public class WithdrawApplyDTO implements Serializable {

    /** 提现金额 */
    @NotNull(message = "请填写提现金额")
    @DecimalMin(value = "0.01", message = "提现金额必须大于 0")
    private BigDecimal amount;

    /** 收款账户类型：bank / alipay / wechat */
    @NotBlank(message = "请选择收款账户类型")
    @Size(max = 20, message = "账户类型过长")
    private String accountType;

    /** 收款账号 */
    @NotBlank(message = "请填写收款账号")
    @Size(max = 100, message = "收款账号过长")
    private String accountNo;

    /** 收款人姓名 */
    @NotBlank(message = "请填写收款人姓名")
    @Size(max = 50, message = "收款人姓名过长")
    private String accountName;
}
