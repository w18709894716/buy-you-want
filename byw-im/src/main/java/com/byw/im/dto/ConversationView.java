package com.byw.im.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 会话列表项视图。unread 为当前查看方的未读数（买家看 userUnread，商家看 shopUnread）。
 * v1 不做 Feign 反查对端昵称/头像，前端按 userId/shopId 结合自身上下文渲染对端信息。
 */
@Data
public class ConversationView {

    private Long id;
    private Long userId;
    private Long shopId;
    /** 店铺名称，买家侧会话列表展示用；商家侧或反查失败时可能为空。 */
    private String shopName;
    private String lastMessage;
    private String lastMessageType;
    private LocalDateTime lastMessageTime;
    private Integer unread;
}
