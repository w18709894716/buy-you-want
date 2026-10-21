package com.byw.common.log.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 操作日志消息体：由 {@code @OperationLog} 切面在方法执行后发出，
 * 由 byw-user 消费并落库到 {@code t_operation_log}，管理端可查。
 *
 * <p>字段长度与 {@code t_operation_log} 的列宽对齐，超长在切面里截断，
 * 避免因为一条审计日志超长导致消费者反复失败。
 */
@Data
public class OperationLogMessage implements Serializable {

    private String module;
    private String operation;
    private String description;

    /** 操作人ID */
    private Long operatorId;
    /** 操作人类型：sys 平台员工 / merchant 商家 / c C端会员 / unknown */
    private String operatorType;
    /** 操作人账号名 */
    private String operatorName;

    /** 类名.方法名 */
    private String method;
    private String requestUri;
    private String requestMethod;
    private String clientIp;

    /** 本次操作是否成功 */
    private boolean success;
    /** 失败原因（成功时为 null） */
    private String errorMsg;
    private Long costMs;

    /** 链路ID：便于与日志文件、其他服务日志对照 */
    private String traceId;
}
