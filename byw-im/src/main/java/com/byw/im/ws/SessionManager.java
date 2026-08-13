package com.byw.im.ws;

import com.byw.common.redis.util.RedisUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Collections;
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
    public static final String ATTR_STAFF_NAME = "staffName";
    public static final String ATTR_IS_MERCHANT = "isMerchant";
    /** 商家账号是否具备客服接待权限（false=无权限，仅维持连接不参与自动分配） */
    public static final String ATTR_CAN_SERVE = "canServe";

    private static final String ONLINE_KEY_PREFIX = "im:online:";
    private static final String STAFF_ONLINE_KEY_PREFIX = "im:online:staff:";
    private static final String SHOP_STAFFS_KEY_PREFIX = "im:online:shop_staffs:";
    private static final long ONLINE_TTL_SECONDS = 60L;

    /** 本节点唯一标识，用于 Redis 在线路由记录 */
    private static final String NODE_ID = UUID.randomUUID().toString();

    private final RedisUtil redisUtil;

    private final ConcurrentHashMap<String, Set<WebSocketSession>> localSessions = new ConcurrentHashMap<>();

    /**
     * 客服个体连接集：key=staffId（merchant_account.id）。
     * 与 localSessions（principal=s:{shopId}，全店客服共享）不同，
     * 用于判断"某个客服是否彻底下线"——仅当其名下所有连接断开才返回 true。
     */
    private final ConcurrentHashMap<Long, Set<WebSocketSession>> staffSessions = new ConcurrentHashMap<>();

    /**
     * 客服挂起状态：key=staffId（merchant_account.id），true=已挂起（不再接新消息）。
     * 存内存+Redis（重启后需客服重新挂起，可接受）。
     */
    private final ConcurrentHashMap<Long, Boolean> suspendedStaff = new ConcurrentHashMap<>();
    private static final String SUSPEND_KEY_PREFIX = "im:staff:suspend:";

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
        // 商家角色额外记录客服个体在线；无客服权限的账号（ATTR_CAN_SERVE=false）不注册，不参与自动分配
        Boolean merchant = (Boolean) session.getAttributes().get(ATTR_IS_MERCHANT);
        if (Boolean.TRUE.equals(merchant)) {
            Long staffId = (Long) session.getAttributes().get(ATTR_USER_ID);
            Long shopId = (Long) session.getAttributes().get(ATTR_SHOP_ID);
            if (staffId != null && shopId != null) {
                Boolean canServe = (Boolean) session.getAttributes().get(ATTR_CAN_SERVE);
                if (!Boolean.FALSE.equals(canServe)) {
                    staffSessions.computeIfAbsent(staffId, k -> ConcurrentHashMap.newKeySet()).add(session);
                    registerMerchantStaff(staffId, shopId);
                }
            }
        }
        log.info("IM 会话注册：principal={}, sessionId={}, 本地在线数={}", principal, session.getId(),
                localSessions.get(principal).size());
    }

    /**
     * 移除会话，返回是否该客服已彻底下线（名下所有连接断开，用于触发会话释放等后续动作）。
     */
    public boolean remove(WebSocketSession session) {
        String principal = (String) session.getAttributes().get(ATTR_PRINCIPAL);
        if (principal == null) {
            return false;
        }
        Set<WebSocketSession> set = localSessions.get(principal);
        if (set != null) {
            set.remove(session);
            if (set.isEmpty()) {
                localSessions.remove(principal);
                redisUtil.delete(ONLINE_KEY_PREFIX + principal);
            }
        }
        // 商家客服：按 staffId 维度判断最后一条连接断开（区别于 principal 店铺级共享集合）
        boolean staffLastClosed = false;
        Boolean merchant = (Boolean) session.getAttributes().get(ATTR_IS_MERCHANT);
        if (Boolean.TRUE.equals(merchant)) {
            Long staffId = (Long) session.getAttributes().get(ATTR_USER_ID);
            Long shopId = (Long) session.getAttributes().get(ATTR_SHOP_ID);
            if (staffId != null && shopId != null) {
                Set<WebSocketSession> sset = staffSessions.get(staffId);
                if (sset != null) {
                    sset.remove(session);
                    if (sset.isEmpty()) {
                        staffSessions.remove(staffId);
                        removeMerchantStaff(staffId, shopId);
                        staffLastClosed = true;
                    }
                }
            }
        }
        log.info("IM 会话移除：principal={}, sessionId={}, staffLastClosed={}", principal, session.getId(), staffLastClosed);
        return staffLastClosed;
    }

    /**
     * 判断客服是否有活跃连接（本节点）。
     * 用于下线释放前的宽限期复查：刷新页面/断线重连期间有连接则不应释放。
     */
    public boolean hasActiveConnection(Long staffId) {
        Set<WebSocketSession> conns = staffSessions.get(staffId);
        if (conns == null) {
            return false;
        }
        for (WebSocketSession s : conns) {
            if (s.isOpen()) {
                return true;
            }
        }
        return false;
    }

    /** 心跳续期本节点在线记录 */
    public void touchOnline(String principal) {
        redisUtil.set(ONLINE_KEY_PREFIX + principal, NODE_ID, ONLINE_TTL_SECONDS, TimeUnit.SECONDS);
    }

    // ========== 客服个体在线追踪 ==========

    /** 注册客服个体在线（商家角色连接时配合 register 调用） */
    public void registerMerchantStaff(Long staffId, Long shopId) {
        String staffKey = STAFF_ONLINE_KEY_PREFIX + staffId;
        redisUtil.set(staffKey, NODE_ID, ONLINE_TTL_SECONDS, TimeUnit.SECONDS);
        String shopStaffsKey = SHOP_STAFFS_KEY_PREFIX + shopId;
        redisUtil.sAdd(shopStaffsKey, staffId);
        redisUtil.expire(shopStaffsKey, ONLINE_TTL_SECONDS, TimeUnit.SECONDS);
    }

    /** 移除客服个体在线 */
    public void removeMerchantStaff(Long staffId, Long shopId) {
        redisUtil.delete(STAFF_ONLINE_KEY_PREFIX + staffId);
        redisUtil.sRemove(SHOP_STAFFS_KEY_PREFIX + shopId, staffId);
    }

    /** 心跳续期客服个体在线（配合 touchOnline 调用） */
    public void touchOnlineStaff(Long staffId, Long shopId) {
        redisUtil.expire(STAFF_ONLINE_KEY_PREFIX + staffId, ONLINE_TTL_SECONDS, TimeUnit.SECONDS);
        redisUtil.expire(SHOP_STAFFS_KEY_PREFIX + shopId, ONLINE_TTL_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * 获取本店当前在线的客服 staffId 列表。
     * 优先以本节点活跃连接为准（连接已关闭的客服不会出现在这里）；
     * Redis 在线集合（im:online:shop_staffs:）作为跨节点在线补充。
     * @return 空 Set 表示无人值班
     */
    @SuppressWarnings("unchecked")
    public Set<Long> getOnlineStaffIds(Long shopId) {
        // 1) 本节点活跃连接（单节点部署下最准确，避免 Redis 残留导致的误分配）
        Set<Long> result = new java.util.HashSet<>();
        staffSessions.forEach((staffId, conns) -> {
            for (WebSocketSession s : conns) {
                if (s.isOpen()) {
                    result.add(staffId);
                    break;
                }
            }
        });
        // 2) Redis 在线集合补充（其他节点在线的客服）
        Set<Object> members = redisUtil.sMembers(SHOP_STAFFS_KEY_PREFIX + shopId);
        if (members != null) {
            for (Object o : members) {
                if (o instanceof Number n) {
                    result.add(n.longValue());
                }
            }
        }
        return result;
    }

    /**
     * 判断客服是否真实在线：本节点有活跃连接，或 Redis 个体在线标记（TTL 60s）仍在。
     * 用于分配前剔除已断线但可能残留在线标记的客服。
     */
    public boolean isStaffOnline(Long staffId) {
        Set<WebSocketSession> conns = staffSessions.get(staffId);
        if (conns != null) {
            for (WebSocketSession s : conns) {
                if (s.isOpen()) {
                    return true;
                }
            }
        }
        return Boolean.TRUE.equals(redisUtil.hasKey(STAFF_ONLINE_KEY_PREFIX + staffId));
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

    // ========== 客服挂起状态 ==========

    /** 判断客服是否处于挂起状态（不接新消息；存量会话可继续回复） */
    public boolean isStaffSuspended(Long staffId) {
        Boolean local = suspendedStaff.get(staffId);
        if (local != null) {
            return local;
        }
        return Boolean.TRUE.equals(redisUtil.hasKey(SUSPEND_KEY_PREFIX + staffId));
    }

    /** 设置客服挂起状态 */
    public void setSuspended(Long staffId, boolean suspended) {
        suspendedStaff.put(staffId, suspended);
        if (suspended) {
            redisUtil.set(SUSPEND_KEY_PREFIX + staffId, "1", 24 * 60 * 60, TimeUnit.SECONDS);
        } else {
            redisUtil.delete(SUSPEND_KEY_PREFIX + staffId);
        }
    }

    /** 获取本店所有挂起中的客服 staffId 集合 */
    public Set<Long> getSuspendedStaffIds(Long shopId) {
        Set<Long> result = new java.util.HashSet<>();
        Set<Long> online = getOnlineStaffIds(shopId);
        for (Long staffId : online) {
            if (isStaffSuspended(staffId)) {
                result.add(staffId);
            }
        }
        return result;
    }
}
