package com.byw.im.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 消息视图：WebSocket 下推与 REST 历史查询统一使用的消息表示。
 */
@Data
public class MessageView {

    private String id;
    private Long conversationId;
    private Long senderId;
    /** user 买家 / merchant 商家 */
    private String senderRole;
    private Long shopId;
    private Long userId;
    /** text / image / product_card / order_card */
    private String type;
    private String content;
    /** 发送者显示名（商家侧为客服姓名） */
    private String senderName;

    /** 引用消息ID（im_messages._id），非空表示该消息为引用消息 */
    private String quoteId;

    /** 被引用消息内容快照（防原消息撤回后引用失效） */
    private String quoteContent;

    /** 被引用消息发送者姓名 */
    private String quoteSenderName;

    /** 是否已撤回（软撤回：内容替换为提示文案，保留记录） */
    private Boolean recalled;

    /** 系统消息类型：assign/transfer/join；普通消息为 null */
    private String systemType;

    private Map<String, Object> extra;
    private Boolean read;
    private LocalDateTime createdAt;
}
