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
    private Map<String, Object> extra;
    private Boolean read;
    private LocalDateTime createdAt;
}
