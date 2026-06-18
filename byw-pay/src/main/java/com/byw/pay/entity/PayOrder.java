package com.byw.pay.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("t_pay_order")
public class PayOrder {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String payNo;

    private String orderNo;

    private Long userId;

    private BigDecimal amount;

    private String payChannel;

    private Integer status;

    private String channelTradeNo;

    private LocalDateTime payTime;

    private String callbackContent;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
