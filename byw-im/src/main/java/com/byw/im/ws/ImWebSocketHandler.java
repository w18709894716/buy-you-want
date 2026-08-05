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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * IM 消息处理器：解析入站 JSON 帧，按 action 分派。
 * <pre>
 * 入站：
 *   {"action":"send","shopId":2,"conversationId":1,"type":"text","content":"hi","extra":{},"quoteId":"..."}
 *   {"action":"recall","conversationId":1,"messageId":"..."}
 *   {"action":"typing","conversationId":1}
 *   {"action":"read","conversationId":1}
 *   {"action":"take","conversationId":1}
 *   {"action":"takeover","conversationId":1}
 *   {"action":"join","conversationId":1}
 *   {"action":"transfer","conversationId":1,"targetStaffId":3}
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

    /** 下线释放复查定时器：连接断开后延迟复查，宽限期内重连则不释放 */
    private static final ScheduledExecutorService RELEASE_CHECKER = Executors.newSingleThreadScheduledExecutor();
    private static final long RELEASE_DELAY_SECONDS = 10L;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessionManager.register(session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        boolean lastClosed = sessionManager.remove(session);
        // 商家客服最后一条连接断开时，延迟复查后再释放其接待中的会话（assignee 置空）。
        // 延迟复查可避免刷新页面/断线重连被误判为彻底下线（旧连接断开、新连接尚未注册的窗口期）。
        if (lastClosed) {
            Boolean merchant = (Boolean) session.getAttributes().get(SessionManager.ATTR_IS_MERCHANT);
            if (Boolean.TRUE.equals(merchant)) {
                Long staffId = (Long) session.getAttributes().get(SessionManager.ATTR_USER_ID);
                Long shopId = (Long) session.getAttributes().get(SessionManager.ATTR_SHOP_ID);
                if (staffId != null && shopId != null) {
                    scheduleReleaseCheck(shopId, staffId);
                }
            }
        }
    }

    /**
     * 宽限期后复查：若该客服仍无任何活跃连接，才视为彻底下线并释放其名下会话；
     * 期间页面刷新/断线重连恢复连接则取消释放。
     */
    private void scheduleReleaseCheck(Long shopId, Long staffId) {
        RELEASE_CHECKER.schedule(() -> {
            try {
                if (sessionManager.hasActiveConnection(staffId)) {
                    log.info("IM 客服宽限期内恢复连接，取消释放：staffId={}", staffId);
                    return;
                }
                log.info("IM 客服下线确认（宽限期后仍无连接），释放名下会话：staffId={}, shopId={}", staffId, shopId);
                imService.releaseStaffConversations(shopId, staffId);
            } catch (Exception e) {
                log.warn("IM 延迟释放客服会话失败：staffId={}, err={}", staffId, e.getMessage());
            }
        }, RELEASE_DELAY_SECONDS, TimeUnit.SECONDS);
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
                        // 商家角色续期客服个体在线
                        if (merchant) {
                            Long staffId = (Long) session.getAttributes().get(SessionManager.ATTR_USER_ID);
                            Long pingShopId = (Long) session.getAttributes().get(SessionManager.ATTR_SHOP_ID);
                            if (staffId != null && pingShopId != null) {
                                sessionManager.touchOnlineStaff(staffId, pingShopId);
                            }
                        }
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
                    cmd.setQuoteId(str(frame.get("quoteId")));
                    cmd.setExtra(asMap(frame.get("extra")));
                    // 商家发送时从握手属性注入客服姓名
                    if (merchant) {
                        cmd.setSenderName((String) session.getAttributes().get(SessionManager.ATTR_STAFF_NAME));
                    }
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
                case "take" -> {
                    // 客服主动接入待接入会话：点开对话框即接入（仅商家角色，assignee 为空时生效）
                    Long conversationId = toLong(frame.get("conversationId"));
                    if (merchant && conversationId != null) {
                        String staffName = (String) session.getAttributes().get(SessionManager.ATTR_STAFF_NAME);
                        imService.takeConversation(conversationId, userId, staffName, sessionShopId);
                    }
                }
                case "takeover" -> {
                    // 客服接管会话：待接入直接接入，已分配则替换接待者（仅商家角色）
                    Long conversationId = toLong(frame.get("conversationId"));
                    if (merchant && conversationId != null) {
                        String staffName = (String) session.getAttributes().get(SessionManager.ATTR_STAFF_NAME);
                        imService.takeOverConversation(conversationId, userId, staffName, sessionShopId);
                    }
                }
                case "join" -> {
                    // 客服介入会话：不影响原接待客服，可共同服务用户（仅商家角色）
                    Long conversationId = toLong(frame.get("conversationId"));
                    if (merchant && conversationId != null) {
                        String staffName = (String) session.getAttributes().get(SessionManager.ATTR_STAFF_NAME);
                        imService.joinConversation(conversationId, userId, staffName, sessionShopId);
                    }
                }
                case "transfer" -> {
                    // 客服转接会话给指定在线客服（仅商家角色，仅接待者可转）
                    Long conversationId = toLong(frame.get("conversationId"));
                    Long targetStaffId = toLong(frame.get("targetStaffId"));
                    if (merchant && conversationId != null && targetStaffId != null) {
                        String staffName = (String) session.getAttributes().get(SessionManager.ATTR_STAFF_NAME);
                        imService.transferConversation(conversationId, userId, staffName, targetStaffId, sessionShopId);
                    }
                }
                case "recall" -> {
                    // 限时撤回消息（仅发送者本人，2 分钟内；失败经 error 帧返回原因）
                    Long conversationId = toLong(frame.get("conversationId"));
                    String messageId = str(frame.get("messageId"));
                    if (conversationId != null && messageId != null) {
                        imService.recallMessage(conversationId, messageId, userId);
                    }
                }
                default -> log.debug("IM 未知 action: {}", action);
            }
        } catch (Exception e) {
            log.warn("IM 处理入站帧失败: {}", e.getMessage());
            // error 帧携带具体原因（如撤回超时/仅发送者可撤回），前端据此提示
            String err = e.getMessage() == null ? "bad frame" : e.getMessage().replace("\\", "/").replace("\"", "'");
            send(session, "{\"action\":\"error\",\"data\":{\"message\":\"" + err + "\"}}");
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
