package com.byw.im.document;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * IM 消息流文档，存 MongoDB im_messages 集合。
 * 会话关系存 MySQL t_conversation，海量只追加的消息存此处。
 */
@Data
@Document("im_messages")
public class ImMessage {

    @Id
    private String id;

    /** 所属会话ID（MySQL t_conversation.id） */
    private Long conversationId;

    /** 发送者用户ID */
    private Long senderId;

    /** 发送者角色 user买家 / merchant商家 */
    private String senderRole;

    /** 会话归属店铺ID（冗余便于查询） */
    private Long shopId;

    /** 会话归属买家ID（冗余便于查询） */
    private Long userId;

    /** 消息类型 text / image / product_card / order_card */
    private String type;

    /** 文本内容或图片URL */
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

    /** 卡片扩展字段：商品卡片{productId,name,image,price}，订单卡片{orderNo,status,items...} */
    private Map<String, Object> extra;

    /** 接收方是否已读 */
    private Boolean read;

    private LocalDateTime createdAt;
}
