package com.byw.im.ws;

import com.byw.common.core.constant.CommonConstants;
import com.byw.im.dto.SendMessageCommand;
import com.byw.im.service.ImService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Map;

/**
 * IM 消息处理器：解析入站 JSON 帧，按 action 分派。
 * <pre>
 * 入站：
 *   {"action":"send","shopId":2,"conversationId":1,"type":"text","content":"hi","extra":{}}
 *   {"action":"typing","conversationId":1}
 *   {"action":"read","conversationId":1}
 *   {"action":"ping"}
 * 出站（下推经 SessionManager）：
 *   {"action":"message","data":{...}} / {"action":"typing",...} / {"action":"read",...} / {"action":"pong"}
 * </pre>
 * 发送/已读/正在输入均经 ImService 落库或广播，广播消费者再回推给两端 principal（含发送方自身，用于多端同步/回执）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ImWebSocketHandler extends TextWebSocketHandler {

    private final ImService imService;
    private final SessionManager sessionManager;
    private final ObjectMapper objectMapper;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessionManager.register(session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessionManager.remove(session);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        Long userId = (Long) session.getAttributes().get(SessionManager.ATTR_USER_ID);
        String role = (String) session.getAttributes().get(SessionManager.ATTR_ROLE);
        String principal = (String) session.getAttributes().get(SessionManager.ATTR_PRINCIPAL);
        Long sessionShopId = (Long) session.getAttributes().get(SessionManager.ATTR_SHOP_ID);
        boolean merchant = CommonConstants.ROLE_MERCHANT_OWNER.equals(role)
                || CommonConstants.ROLE_MERCHANT_STAFF.equals(role);
        String senderRole = merchant ? "merchant" : "user";

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> frame = objectMapper.readValue(message.getPayload(), Map.class);
            String action = str(frame.get("action"));
            if (action == null) {
                return;
            }
            switch (action) {
                case "ping" -> {
                    if (principal != null) {
                        sessionManager.touchOnline(principal);
                    }
                    send(session, "{\"action\":\"pong\"}");
                }
                case "send" -> {
                    SendMessageCommand cmd = new SendMessageCommand();
                    cmd.setSenderId(userId);
                    cmd.setSenderRole(senderRole);
                    // 商家发送用握手 shopId，买家发送用帧内 shopId
                    cmd.setShopId(merchant ? sessionShopId : toLong(frame.get("shopId")));
                    cmd.setConversationId(toLong(frame.get("conversationId")));
                    cmd.setType(str(frame.get("type")));
                    cmd.setContent(str(frame.get("content")));
                    cmd.setExtra(asMap(frame.get("extra")));
                    imService.sendMessage(cmd);
                }
                case "typing" -> {
                    Long conversationId = toLong(frame.get("conversationId"));
                    if (conversationId != null) {
                        imService.broadcastTyping(conversationId, senderRole);
                    }
                }
                case "read" -> {
                    Long conversationId = toLong(frame.get("conversationId"));
                    if (conversationId != null) {
                        imService.markRead(conversationId, senderRole);
                    }
                }
                default -> log.debug("IM 未知 action: {}", action);
            }
        } catch (Exception e) {
            log.warn("IM 处理入站帧失败: {}", e.getMessage());
            send(session, "{\"action\":\"error\",\"data\":{\"message\":\"bad frame\"}}");
        }
    }

    private void send(WebSocketSession session, String payload) {
        try {
            synchronized (session) {
                if (session.isOpen()) {
                    session.sendMessage(new TextMessage(payload));
                }
            }
        } catch (Exception e) {
            log.warn("IM 发送失败: {}", e.getMessage());
        }
    }

    private static String str(Object o) {
        return o == null ? null : o.toString();
    }

    private static Long toLong(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.valueOf(o.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object o) {
        return o instanceof Map ? (Map<String, Object>) o : null;
    }
}
