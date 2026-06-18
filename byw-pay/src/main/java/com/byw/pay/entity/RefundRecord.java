package com.byw.pay.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("t_refund_record")
public class RefundRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String refundNo;

    private String payNo;

    private String orderNo;

    private Long userId;

    private BigDecimal refundAmount;

    private String reason;

    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
