package com.byw.api.settle.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 结算单（每个已收货子订单一条）。
 */
@Data
public class SettleRecordDTO implements Serializable {

    private Long id;

    private String settleNo;

    /** 子订单号 */
    private String orderNo;

    /** 父订单号 */
    private String parentOrderNo;

    private Long shopId;

    /** 店铺名称（回填展示用） */
    private String shopName;

    private Long userId;

    /** 订单实付金额（结算基数） */
    private BigDecimal orderAmount;

    /** 平台佣金 */
    private BigDecimal commissionAmount;

    /** 商家应得金额（orderAmount - commissionAmount） */
    private BigDecimal settleAmount;

    /** 结算状态：0 待结算(冷静期冻结) 1 已入账 2 已关闭(退款) */
    private Integer status;

    /** 收货时间 */
    private LocalDateTime receiveTime;

    /** 预计入账时间（收货 + T+N） */
    private LocalDateTime expectSettleTime;

    /** 实际入账时间 */
    private LocalDateTime settleTime;

    private LocalDateTime createdAt;
}
