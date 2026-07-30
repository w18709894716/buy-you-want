package com.byw.order.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("t_order")
public class Order {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String orderNo;

    /** 父订单号（子订单指向父订单；父订单本身为空） */
    private String parentOrderNo;

    /** 是否父订单：0子/普通订单 1父订单(仅聚合支付) */
    private Integer isParent;

    private Long userId;

    /** 归属店铺ID（多租户维度；多商家拆单后每个子订单归属单一店铺） */
    private Long shopId;

    private BigDecimal totalAmount;

    private BigDecimal payAmount;

    private BigDecimal freightAmount;

    private BigDecimal discountAmount;

    private Long couponId;

    private Integer status;

    private String receiverName;

    private String receiverPhone;

    private String receiverAddress;

    private String remark;

    private LocalDateTime payTime;

    private LocalDateTime shipTime;

    private LocalDateTime receiveTime;

    private LocalDateTime cancelTime;

    private String cancelReason;

    /** 关闭类型 null未关闭 1取消关闭 2退款关闭 */
    private Integer closeType;

    /** 是否已评价 0未评价 1已评价 */
    private Integer reviewed;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
