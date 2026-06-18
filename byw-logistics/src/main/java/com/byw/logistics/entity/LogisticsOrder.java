package com.byw.logistics.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_logistics_order")
public class LogisticsOrder {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String orderNo;

    private String companyCode;

    private String companyName;

    private String trackingNo;

    private String senderName;

    private String senderPhone;

    private String senderAddress;

    private String receiverName;

    private String receiverPhone;

    private String receiverAddress;

    /** 0已揽收 1运输中 2派送中 3已签收 */
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
