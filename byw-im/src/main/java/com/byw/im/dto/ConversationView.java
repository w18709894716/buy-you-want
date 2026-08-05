package com.byw.im.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 会话列表项视图。unread 为当前查看方的未读数（买家看 userUnread，商家看 shopUnread）。
 */
@Data
public class ConversationView {

    private Long id;
    private Long userId;
    /** 买家昵称，商家侧会话列表/聊天框展示用；反查失败时可能为空。 */
    private String userNickname;
    private Long shopId;
    /** 店铺名称，买家侧会话列表展示用；商家侧或反查失败时可能为空。 */
    private String shopName;
    private String lastMessage;
    private String lastMessageType;
    private LocalDateTime lastMessageTime;
    private Integer unread;

    /** 当前接待客服ID */
    private Long assigneeId;

    /** 接待客服姓名 */
    private String assigneeName;

    /** 介入客服ID集合（介入不影响原接待客服，可共同服务用户） */
    private List<Long> joiners;
}
