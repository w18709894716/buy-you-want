package com.byw.api.user.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 操作审计日志（t_operation_log）传输对象：管理端列表展示用。
 */
@Data
public class OperationLogDTO implements Serializable {

    private Long id;
    /** 模块：商品/订单/售后/营销/权限/用户… */
    private String module;
    /** 操作：新增/修改/删除/审核/发货… */
    private String operation;
    private String description;
    private Long operatorId;
    /** 操作人类型：sys 平台员工 / merchant 商家 / c C端会员 / unknown */
    private String operatorType;
    private String operatorName;
    /** 类名.方法名 */
    private String method;
    private String requestUri;
    private String requestMethod;
    private String clientIp;
    /** 1成功 0失败 */
    private Integer success;
    private String errorMsg;
    private Long costMs;
    private String traceId;
    private LocalDateTime createdAt;
}
