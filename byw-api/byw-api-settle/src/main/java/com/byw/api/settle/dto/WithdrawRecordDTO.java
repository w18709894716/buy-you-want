package com.byw.api.settle.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 提现单。
 */
@Data
public class WithdrawRecordDTO implements Serializable {

    private Long id;

    private String withdrawNo;

    private Long shopId;

    /** 店铺名称（平台侧列表回填展示用） */
    private String shopName;

    /** 提现金额 */
    private BigDecimal amount;

    /** 状态：0 待审核 1 通过(已打款) 2 驳回 */
    private Integer status;

    /** 收款账户类型：bank / alipay / wechat */
    private String accountType;

    /** 收款账号 */
    private String accountNo;

    /** 收款人姓名 */
    private String accountName;

    /** 申请时间 */
    private LocalDateTime applyTime;

    /** 审核时间 */
    private LocalDateTime auditTime;

    /** 审核人 */
    private String auditor;

    /** 驳回原因 */
    private String rejectReason;

    private LocalDateTime createdAt;
}
