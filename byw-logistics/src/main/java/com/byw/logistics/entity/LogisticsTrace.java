package com.byw.logistics.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_logistics_trace")
public class LogisticsTrace {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long logisticsId;

    private String trackingNo;

    private String description;

    private String location;

    private LocalDateTime traceTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
