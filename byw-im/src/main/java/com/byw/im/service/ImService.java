package com.byw.im.service;

import com.byw.common.core.result.PageResult;
import com.byw.im.dto.ConversationView;
import com.byw.im.dto.MessageView;
import com.byw.im.dto.SendMessageCommand;
import com.byw.im.entity.Conversation;

import java.util.List;

/**
 * 客服 IM 领域服务：会话获取、消息收发落库、未读维护、已读/正在输入信令广播。
 */
public interface ImService {

    /** 按 (userId, shopId) 获取或创建唯一会话 */
    Conversation getOrCreateConversation(Long userId, Long shopId);

    /** 发送消息：会话落库 + 消息入 Mongo + 更新未读 + RocketMQ 广播下推 */
    MessageView sendMessage(SendMessageCommand command);

    /** 会话列表：买家查自己(role=user, 传 userId)，商家查本店(role=merchant, 传 shopId) */
    List<ConversationView> listConversations(Long userId, Long shopId, String role);

    /** 会话历史消息分页（按时间倒序） */
    PageResult<MessageView> listMessages(Long conversationId, int page, int size);

    /** 标记会话已读：清对应未读 + 标记消息 read + 广播 read 信令 */
    void markRead(Long conversationId, String readerRole);

    /** 未读总数（角标）：买家汇总 userUnread，商家汇总本店 shopUnread */
    long unreadTotal(Long userId, Long shopId, String role);

    /** 广播"正在输入"信令（不落库） */
    void broadcastTyping(Long conversationId, String senderRole);
}
