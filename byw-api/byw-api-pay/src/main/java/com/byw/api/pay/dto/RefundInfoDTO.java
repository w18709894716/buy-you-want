package com.byw.api.pay.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 退款明细信息（供订单侧组装退款流程展示使用）
 */
@Data
public class RefundInfoDTO implements Serializable {
    /** 退款单号 */
    private String refundNo;
    private String orderNo;
    /** 退款金额 */
    private BigDecimal refundAmount;
    /** 退款原因 */
    private String reason;
    /** 退款状态 0处理中 1成功 2失败 */
    private Integer status;
    /** 原支付渠道（原路退回目的地） */
    private String payChannel;
    /** 退款发起时间 */
    private LocalDateTime createdAt;
    /** 退款到账/更新时间 */
    private LocalDateTime updatedAt;
}
