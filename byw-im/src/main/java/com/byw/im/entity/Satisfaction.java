package com.byw.im.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * IM 满意度评价：用户对客服会话的 1-5 星评价。
 * 一会话只允许评价一次（uk_conversation）。
 */
@Data
@TableName("t_im_satisfaction")
public class Satisfaction {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 店铺ID */
    private Long shopId;

    /** 会话ID（唯一约束） */
    private Long conversationId;

    /** 评价用户ID */
    private Long userId;

    /** 接待客服ID（评价对象） */
    private Long staffId;

    /** 客服姓名（评价时快照，防改名后混淆） */
    private String staffName;

    /** 评分 1-5 */
    private Integer rating;

    /** 评价标签（逗号分隔） */
    private String tags;

    /** 留言 */
    private String comment;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}