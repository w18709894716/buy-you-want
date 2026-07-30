package com.byw.order.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("t_after_sale")
public class AfterSale {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 售后单号 */
    private String afterSaleNo;

    /** 关联订单号 */
    private String orderNo;

    /** 关联订单明细ID（NULL=历史订单级售后） */
    private Long orderItemId;

    /** 商品名称快照 */
    private String productName;

    /** SKU规格快照 */
    private String skuName;

    /** 商品图片快照 */
    private String productImage;

    private Long userId;

    /** 归属店铺ID（多租户维度） */
    private Long shopId;

    /** 售后类型 1仅退款 2退货退款 3换货 4维修 5补寄 6价保 */
    private Integer type;

    /** 申请原因 */
    private String reason;

    /** 问题描述 */
    private String description;

    /** 申请退款金额（仅退款/退货退款/价保时有值） */
    private BigDecimal refundAmount;

    /** 售后状态 0待审核 1待买家寄回 2已拒绝 3已完成 4已撤销 5待商家收货 6退款中 */
    private Integer status;

    /** 拒绝原因 */
    private String rejectReason;

    /** 审核通过时间 */
    private LocalDateTime approveTime;

    /** 买家寄回物流公司 */
    private String returnCompany;

    /** 买家寄回运单号 */
    private String returnTrackingNo;

    /** 买家寄回时间 */
    private LocalDateTime returnShipTime;

    /** 商家确认收货时间 */
    private LocalDateTime receiveTime;

    /** 完成时间 */
    private LocalDateTime finishTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
