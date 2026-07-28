package com.byw.settle.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 结算单（每个已收货子订单一条）。
 */
@Data
@TableName("t_settle_record")
public class SettleRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 结算单号 */
    private String settleNo;

    /** 子订单号 */
    private String orderNo;

    /** 父订单号 */
    private String parentOrderNo;

    /** 归属店铺ID */
    private Long shopId;

    /** 下单用户ID */
    private Long userId;

    /** 订单实付(结算基数) */
    private BigDecimal orderAmount;

    /** 平台佣金 */
    private BigDecimal commissionAmount;

    /** 商家应得 */
    private BigDecimal settleAmount;

    /** 0待结算(冷静期冻结) 1已入账 2已关闭(退款) */
    private Integer status;

    /** 收货时间 */
    private LocalDateTime receiveTime;

    /** 预计入账时间(收货+T+N) */
    private LocalDateTime expectSettleTime;

    /** 实际入账时间 */
    private LocalDateTime settleTime;

    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
