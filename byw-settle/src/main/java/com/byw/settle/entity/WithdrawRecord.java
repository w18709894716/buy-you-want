package com.byw.settle.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 提现单。
 */
@Data
@TableName("t_withdraw_record")
public class WithdrawRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 提现单号 */
    private String withdrawNo;

    /** 归属店铺ID */
    private Long shopId;

    /** 提现金额 */
    private BigDecimal amount;

    /** 0待审核 1通过(已打款) 2驳回 */
    private Integer status;

    /** 收款账户类型 bank/alipay/wechat */
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

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
