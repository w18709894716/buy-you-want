package com.byw.logistics.entity;

import jakarta.validation.constraints.Size;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_logistics_trace")
public class LogisticsTrace {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long logisticsId;

    @Size(max = 100, message = "运单号过长")
    private String trackingNo;

    @Size(max = 500, message = "物流描述过长")
    private String description;

    @Size(max = 200, message = "物流地点过长")
    private String location;

    private LocalDateTime traceTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
