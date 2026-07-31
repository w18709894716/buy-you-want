package com.byw.im.ws;

import com.byw.common.redis.util.RedisUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 本地会话管理：维护 principal -> 一组 WebSocketSession 的映射（同一主体可能多端/多标签在线）。
 * 同时把在线状态写入 Redis（im:online:{principal} -> nodeId, TTL 60s），供跨节点路由参考。
 * <p>principal 约定：买家为 {@code u:<userId>}，商家（店主/员工共享）为 {@code s:<shopId>}。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SessionManager {

    public static final String ATTR_USER_ID = "userId";
    public static final String ATTR_ROLE = "role";
    public static final String ATTR_SHOP_ID = "shopId";
    public static final String ATTR_PRINCIPAL = "principal";

    private static final String ONLINE_KEY_PREFIX = "im:online:";
    private static final long ONLINE_TTL_SECONDS = 60L;

    /** 本节点唯一标识，用于 Redis 在线路由记录 */
    private static final String NODE_ID = UUID.randomUUID().toString();

    private final RedisUtil redisUtil;

    private final ConcurrentHashMap<String, Set<WebSocketSession>> localSessions = new ConcurrentHashMap<>();

    public static String userPrincipal(Long userId) {
        return "u:" + userId;
    }

    public static String shopPrincipal(Long shopId) {
        return "s:" + shopId;
    }

    public void register(WebSocketSession session) {
        String principal = (String) session.getAttributes().get(ATTR_PRINCIPAL);
        if (principal == null) {
            return;
        }
        localSessions.computeIfAbsent(principal, k -> ConcurrentHashMap.newKeySet()).add(session);
        touchOnline(principal);
        log.info("IM 会话注册：principal={}, sessionId={}, 本地在线数={}", principal, session.getId(),
                localSessions.get(principal).size());
    }

    public void remove(WebSocketSession session) {
        String principal = (String) session.getAttributes().get(ATTR_PRINCIPAL);
        if (principal == null) {
            return;
        }
        Set<WebSocketSession> set = localSessions.get(principal);
        if (set != null) {
            set.remove(session);
            if (set.isEmpty()) {
                localSessions.remove(principal);
                redisUtil.delete(ONLINE_KEY_PREFIX + principal);
            }
        }
        log.info("IM 会话移除：principal={}, sessionId={}", principal, session.getId());
    }

    /** 心跳续期本节点在线记录 */
    public void touchOnline(String principal) {
        redisUtil.set(ONLINE_KEY_PREFIX + principal, NODE_ID, ONLINE_TTL_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * 向指定 principal 的所有本地会话下推文本帧。返回本节点成功投递的会话数。
     */
    public int sendToPrincipal(String principal, String payload) {
        Set<WebSocketSession> set = localSessions.get(principal);
        if (set == null || set.isEmpty()) {
            return 0;
        }
        int sent = 0;
        TextMessage frame = new TextMessage(payload);
        for (WebSocketSession session : set) {
            if (!session.isOpen()) {
                set.remove(session);
                continue;
            }
            try {
                synchronized (session) {
                    session.sendMessage(frame);
                }
                sent++;
            } catch (IOException e) {
                log.warn("IM 下推失败：principal={}, sessionId={}, err={}", principal, session.getId(), e.getMessage());
            }
        }
        return sent;
    }
}
