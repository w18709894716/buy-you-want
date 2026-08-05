package com.byw.im.service;

import com.byw.common.core.result.PageResult;
import com.byw.im.dto.ConversationView;
import com.byw.im.dto.MessageView;
import com.byw.im.dto.SendMessageCommand;
import com.byw.im.dto.StaffBriefDTO;
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

    /** 限时撤回消息：仅发送者本人，2 分钟内（软撤回：内容替换为提示文案，保留记录） */
    void recallMessage(Long conversationId, String messageId, Long operatorId);

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

    /** 自动分配会话给最空闲的在线客服（用户发送消息且会话无接待客服时触发） */
    void autoAssignConversation(Long conversationId, Long shopId);

    /** 客服主动接入待接入会话（assignee 为空时生效，不抢占已分配会话） */
    void takeConversation(Long conversationId, Long staffId, String staffName, Long shopId);

    /** 接管会话：待接入直接接入；已分配则替换接待者为当前客服（已是接待者则跳过） */
    void takeOverConversation(Long conversationId, Long staffId, String staffName, Long shopId);

    /** 介入会话：不影响原接待客服，把自己加入介入者集合，可共同服务用户（待接入会话则直接接管） */
    void joinConversation(Long conversationId, Long staffId, String staffName, Long shopId);

    /** 转接会话：仅当前接待客服可转，目标须为本店在线且有客服权限的客服 */
    void transferConversation(Long conversationId, Long operatorId, String operatorName,
                              Long targetStaffId, Long shopId);

    /** 本店可接待在线客服列表（转接选人，仅在线且活跃的客服） */
    List<StaffBriefDTO> listOnlineStaff(Long shopId);

    /** 客服彻底下线时释放其名下接待中的会话（assignee 置空，下次用户发消息自动重新分配） */
    void releaseStaffConversations(Long shopId, Long staffId);
}
