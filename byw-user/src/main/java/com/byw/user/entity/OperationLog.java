package com.byw.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 操作审计日志（t_operation_log）。
 *
 * <p>刻意不加逻辑删除：审计日志只追加、不修改不删除。
 */
@Data
@TableName("t_operation_log")
public class OperationLog {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String module;
    private String operation;
    private String description;
    private Long operatorId;
    private String operatorType;
    private String operatorName;
    private String method;
    private String requestUri;
    private String requestMethod;
    private String clientIp;
    private Integer success;
    private String errorMsg;
    private Long costMs;
    private String traceId;
    private LocalDateTime createdAt;
}
