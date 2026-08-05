package com.byw.im.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 客服会话：一个用户对一个店铺唯一一条会话。
 * 商家多客服（店主/员工）共享本店会话。
 */
@Data
@TableName("t_conversation")
public class Conversation {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 买家用户ID */
    private Long userId;

    /** 归属店铺ID（多租户维度） */
    private Long shopId;

    /** 最后一条消息摘要（文本内容或消息类型的展示文案） */
    private String lastMessage;

    /** 最后一条消息类型 text/image/product_card/order_card */
    private String lastMessageType;

    /** 最后一条消息时间 */
    private LocalDateTime lastMessageTime;

    /** 买家未读数 */
    private Integer userUnread;

    /** 商家未读数 */
    private Integer shopUnread;

    /** 当前接待客服ID（merchant_account.id） */
    private Long assigneeId;

    /** 接待客服姓名 */
    private String assigneeName;

    /** 介入客服ID集合（JSON数组，如 "[3,5]"；介入不影响原接待客服，可共同服务用户） */
    private String joiners;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
