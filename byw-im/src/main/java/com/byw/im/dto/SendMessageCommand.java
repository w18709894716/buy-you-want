package com.byw.im.dto;

import lombok.Data;

import java.util.Map;

/**
 * 发送消息命令：由 WebSocket send 帧或 REST 组装，交给 ImService 落库与广播。
 */
@Data
public class SendMessageCommand {

    /** 发送者用户ID（来自握手身份） */
    private Long senderId;
    /** 发送者角色 user / merchant */
    private String senderRole;
    /** 目标店铺ID */
    private Long shopId;
    /** 已存在会话可直接带上；为空则按 (userId, shopId) 获取或创建 */
    private Long conversationId;
    /** text / image / product_card / order_card */
    private String type;
    private String content;
    private Map<String, Object> extra;
}
