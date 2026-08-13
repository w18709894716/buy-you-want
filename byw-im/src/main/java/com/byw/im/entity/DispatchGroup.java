package com.byw.im.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * IM 客服分流分组（职能组）：商家自定义分组只表达"哪组客服处理哪块问题"，
 * 不再承担匹配条件/优先级/默认兜底；不建分组时全店在线客服均衡分配。
 */
@Data
@TableName("t_im_dispatch_group")
public class DispatchGroup {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 店铺ID */
    private Long shopId;

    /** 分流分组名称（售前/售后…） */
    private String groupName;

    /** 组内客服同时接待最大人数（按未结束服务数计） */
    private Integer maxConcurrent;

    /** 状态 0禁用 1启用 */
    private Integer status;

    /** 组内客服数（非持久化，列表展示用） */
    @TableField(exist = false)
    private Integer staffCount;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
