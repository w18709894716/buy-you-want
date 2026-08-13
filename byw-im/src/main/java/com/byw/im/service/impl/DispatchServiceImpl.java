package com.byw.im.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.byw.api.shop.ShopFeignClient;
import com.byw.api.shop.dto.MerchantAccountDTO;
import com.byw.api.user.RbacFeignClient;
import com.byw.common.core.exception.BusinessException;
import com.byw.common.core.result.R;
import com.byw.im.document.ImMessage;
import com.byw.im.dto.DispatchResolveResult;
import com.byw.im.dto.DispatchStats;
import com.byw.im.dto.ImBroadcast;
import com.byw.im.dto.MessageView;
import com.byw.im.entity.Conversation;
import com.byw.im.entity.DispatchGroup;
import com.byw.im.entity.DispatchGroupStaff;
import com.byw.im.entity.DispatchRule;
import com.byw.im.entity.ServiceRecord;
import com.byw.im.mapper.ConversationMapper;
import com.byw.im.mapper.DispatchGroupMapper;
import com.byw.im.mapper.DispatchGroupStaffMapper;
import com.byw.im.mapper.DispatchRuleMapper;
import com.byw.im.mapper.ServiceRecordMapper;
import com.byw.im.producer.ImEventProducer;
import com.byw.im.service.DispatchService;
import com.byw.im.service.ServiceRecordService;
import com.byw.im.ws.SessionManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * IM 客服分流实现：
 * <ul>
 *   <li>分组 = 职能（只表达哪组客服处理哪块问题），删除时校验被启用规则引用</li>
 *   <li>规则 = 分流策略（条件/服务时间/回头客/机器人优先），命中后进入绑定分组</li>
 *   <li>队列/离线池：组内繁忙 → QUEUEING；无人可接 → OFFLINE_POOL；触发点消费</li>
 *   <li>核心选人：有工作台权限子账号（为空时主账号兜底）∩ 在线 ∩ 未挂起；组内分配排除达 max_concurrent 的客服</li>
 * </ul>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class DispatchServiceImpl implements DispatchService {

    /** 非服务时间默认提示语 */
    public static final String DEFAULT_OFF_HOURS_TIP = "亲，当前为非工作时间，客服暂未在线。您可在工作时间再次咨询，我们会第一时间为您服务。";

    /** 工作台权限标识（商家子账号/主账号据此判定可参与自动分配） */
    private static final String PERM_IM_WORKBENCH = "m:im:workbench";

    /** 商家账号 userType（RBAC 契约：2=商家账号） */
    private static final int USER_TYPE_MERCHANT = 2;

    private final DispatchGroupMapper dispatchGroupMapper;
    private final DispatchGroupStaffMapper dispatchGroupStaffMapper;
    private final DispatchRuleMapper dispatchRuleMapper;
    private final ConversationMapper conversationMapper;
    private final ServiceRecordMapper serviceRecordMapper;
    private final SessionManager sessionManager;
    private final MongoTemplate mongoTemplate;
    private final ImEventProducer imEventProducer;
    private final ShopFeignClient shopFeignClient;
    private final RbacFeignClient rbacFeignClient;
    private final ServiceRecordService serviceRecordService;

    // ========== 分流分组 CRUD ==========

    @Override
    public List<DispatchGroup> listByShop(Long shopId) {
        List<DispatchGroup> groups = dispatchGroupMapper.selectList(
                new LambdaQueryWrapper<DispatchGroup>()
                        .eq(DispatchGroup::getShopId, shopId)
                        .orderByAsc(DispatchGroup::getId));
        // 填充组内客服数（列表展示用）
        for (DispatchGroup g : groups) {
            g.setStaffCount(Math.toIntExact(dispatchGroupStaffMapper.selectCount(
                    new LambdaQueryWrapper<DispatchGroupStaff>()
                            .eq(DispatchGroupStaff::getGroupId, g.getId()))));
        }
        return groups;
    }

    @Override
    public DispatchGroup create(DispatchGroup group) {
        if (group.getMaxConcurrent() == null || group.getMaxConcurrent() < 1) {
            group.setMaxConcurrent(5);
        }
        if (group.getStatus() == null) {
            group.setStatus(1);
        }
        dispatchGroupMapper.insert(group);
        return group;
    }

    @Override
    public DispatchGroup update(DispatchGroup group) {
        requireOwnedGroup(group.getId(), group.getShopId());
        if (group.getMaxConcurrent() != null && group.getMaxConcurrent() < 1) {
            group.setMaxConcurrent(5);
        }
        dispatchGroupMapper.updateById(group);
        return group;
    }

    @Override
    @Transactional
    public void delete(Long id, Long shopId) {
        requireOwnedGroup(id, shopId);
        // 被启用规则引用的分组拒绝删除，防止规则落到空分组
        long refCount = dispatchRuleMapper.selectCount(new LambdaQueryWrapper<DispatchRule>()
                .eq(DispatchRule::getGroupId, id)
                .eq(DispatchRule::getEnabled, 1));
        if (refCount > 0) {
            throw new BusinessException("该分组正被启用中的分流规则引用，请先停用或删除相关规则");
        }
        dispatchGroupMapper.deleteById(id);
        dispatchGroupStaffMapper.delete(new LambdaQueryWrapper<DispatchGroupStaff>()
                .eq(DispatchGroupStaff::getGroupId, id));
        log.info("IM 删除分流分组：id={}, shopId={}", id, shopId);
    }

    @Override
    public List<DispatchGroupStaff> getStaffGroups(Long staffId) {
        return dispatchGroupStaffMapper.selectList(
                new LambdaQueryWrapper<DispatchGroupStaff>()
                        .eq(DispatchGroupStaff::getStaffId, staffId));
    }

    @Override
    @Transactional
    public void saveStaffGroups(Long staffId, Map<Long, Integer> groupWeights) {
        // 同一客服只能在一个分组：多于一个直接拒绝
        if (groupWeights != null && groupWeights.size() > 1) {
            throw new BusinessException("同一客服只能属于一个分流分组");
        }
        // 先删
        dispatchGroupStaffMapper.delete(
                new LambdaQueryWrapper<DispatchGroupStaff>().eq(DispatchGroupStaff::getStaffId, staffId));
        // 后插
        if (groupWeights != null) {
            for (Map.Entry<Long, Integer> e : groupWeights.entrySet()) {
                DispatchGroupStaff sgs = new DispatchGroupStaff();
                sgs.setGroupId(e.getKey());
                sgs.setStaffId(staffId);
                sgs.setWeight(e.getValue() == null || e.getValue() < 1 ? 1 : e.getValue());
                dispatchGroupStaffMapper.insert(sgs);
            }
        }
    }

    @Override
    @Transactional
    public void saveGroupStaff(Long groupId, Long shopId, Map<Long, Integer> staffWeights) {
        // 租户校验：仅能维护本店分组
        requireOwnedGroup(groupId, shopId);
        // 同一客服只能在一个分组：待绑定客服不得已属于其他分组
        if (staffWeights != null && !staffWeights.isEmpty()) {
            List<DispatchGroupStaff> conflicts = dispatchGroupStaffMapper.selectList(
                    new LambdaQueryWrapper<DispatchGroupStaff>()
                            .ne(DispatchGroupStaff::getGroupId, groupId)
                            .in(DispatchGroupStaff::getStaffId, staffWeights.keySet()));
            if (!conflicts.isEmpty()) {
                DispatchGroupStaff c = conflicts.get(0);
                DispatchGroup other = dispatchGroupMapper.selectById(c.getGroupId());
                throw new IllegalArgumentException("客服 "
                        + resolveAssigneeName(c.getStaffId(), String.valueOf(c.getStaffId()))
                        + " 已属于分组「" + (other != null ? other.getGroupName() : c.getGroupId())
                        + "」，同一客服只能属于一个分组");
            }
        }
        // 先删
        dispatchGroupStaffMapper.delete(
                new LambdaQueryWrapper<DispatchGroupStaff>().eq(DispatchGroupStaff::getGroupId, groupId));
        // 后插
        if (staffWeights != null) {
            for (Map.Entry<Long, Integer> e : staffWeights.entrySet()) {
                DispatchGroupStaff sgs = new DispatchGroupStaff();
                sgs.setGroupId(groupId);
                sgs.setStaffId(e.getKey());
                sgs.setWeight(e.getValue() == null || e.getValue() < 1 ? 1 : e.getValue());
                dispatchGroupStaffMapper.insert(sgs);
            }
        }
    }

    @Override
    public Map<Long, Long> getStaffGroupMap(Long shopId) {
        List<DispatchGroup> groups = listByShop(shopId);
        if (groups.isEmpty()) {
            return Collections.emptyMap();
        }
        Set<Long> groupIds = groups.stream().map(DispatchGroup::getId).collect(Collectors.toSet());
        List<DispatchGroupStaff> all = dispatchGroupStaffMapper.selectList(
                new LambdaQueryWrapper<DispatchGroupStaff>()
                        .in(DispatchGroupStaff::getGroupId, groupIds));
        Map<Long, Long> result = new LinkedHashMap<>();
        for (DispatchGroupStaff s : all) {
            result.putIfAbsent(s.getStaffId(), s.getGroupId());
        }
        return result;
    }

    // ========== 分流规则 CRUD ==========

    @Override
    public List<DispatchRule> listRules(Long shopId) {
        List<DispatchRule> rules = dispatchRuleMapper.selectList(
                new LambdaQueryWrapper<DispatchRule>()
                        .eq(DispatchRule::getShopId, shopId)
                        .orderByAsc(DispatchRule::getPriority)
                        .orderByAsc(DispatchRule::getId));
        if (rules.isEmpty()) {
            return rules;
        }
        // 填充分组名快照
        Map<Long, String> groupNameMap = dispatchGroupMapper.selectBatchIds(
                        rules.stream().map(DispatchRule::getGroupId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(DispatchGroup::getId, DispatchGroup::getGroupName, (a, b) -> a));
        for (DispatchRule r : rules) {
            r.setGroupName(groupNameMap.get(r.getGroupId()));
        }
        return rules;
    }

    @Override
    public DispatchRule createRule(DispatchRule rule) {
        requireOwnedGroup(rule.getGroupId(), rule.getShopId());
        if (rule.getPriority() == null) {
            rule.setPriority(0);
        }
        if (rule.getEnabled() == null) {
            rule.setEnabled(1);
        }
        if (rule.getRobotFirst() == null) {
            rule.setRobotFirst(0);
        }
        if (rule.getRepeatCustomer() == null) {
            rule.setRepeatCustomer(0);
        }
        dispatchRuleMapper.insert(rule);
        log.info("IM 新增分流规则：shopId={}, ruleId={}, groupId={}", rule.getShopId(), rule.getId(), rule.getGroupId());
        return rule;
    }

    @Override
    public DispatchRule updateRule(DispatchRule rule) {
        DispatchRule exist = dispatchRuleMapper.selectById(rule.getId());
        if (exist == null || !rule.getShopId().equals(exist.getShopId())) {
            throw new BusinessException("规则不存在");
        }
        requireOwnedGroup(rule.getGroupId(), rule.getShopId());
        dispatchRuleMapper.updateById(rule);
        return rule;
    }

    @Override
    public void deleteRule(Long id, Long shopId) {
        DispatchRule exist = dispatchRuleMapper.selectById(id);
        if (exist == null || !shopId.equals(exist.getShopId())) {
            throw new BusinessException("规则不存在");
        }
        dispatchRuleMapper.deleteById(id);
        log.info("IM 删除分流规则：id={}, shopId={}", id, shopId);
    }

    @Override
    public void toggleRule(Long id, Long shopId, boolean enabled) {
        DispatchRule exist = dispatchRuleMapper.selectById(id);
        if (exist == null || !shopId.equals(exist.getShopId())) {
            throw new BusinessException("规则不存在");
        }
        dispatchRuleMapper.update(null, new LambdaUpdateWrapper<DispatchRule>()
                .eq(DispatchRule::getId, id)
                .set(DispatchRule::getEnabled, enabled ? 1 : 0));
    }

    // ========== 规则解析 ==========

    @Override
    public DispatchResolveResult resolveDispatchRule(String intent, Integer orderStatus, Long userId, Long shopId) {
        List<DispatchRule> rules = dispatchRuleMapper.selectList(
                new LambdaQueryWrapper<DispatchRule>()
                        .eq(DispatchRule::getShopId, shopId)
                        .eq(DispatchRule::getEnabled, 1)
                        .orderByAsc(DispatchRule::getPriority)
                        .orderByAsc(DispatchRule::getId));
        if (rules.isEmpty()) {
            // 无启用规则：全天候基础分流
            return new DispatchResolveResult(null, null, true, null, false, null);
        }
        LocalDateTime now = LocalDateTime.now();
        String fallbackTip = null;
        DispatchRule activeRule = null;
        for (DispatchRule rule : rules) {
            // 不在服务时间内 → 跳过（记录最高优先级规则的非服务时间提示语）
            if (!inServiceTime(rule, now)) {
                if (fallbackTip == null && rule.getOffHoursTip() != null && !rule.getOffHoursTip().isBlank()) {
                    fallbackTip = rule.getOffHoursTip();
                }
                continue;
            }
            if (activeRule == null) {
                activeRule = rule;
            }
            // 回头客：开启且窗口内最近接待过该用户（本店）的客服在线 → 直接分配该客服
            if (Integer.valueOf(1).equals(rule.getRepeatCustomer()) && userId != null) {
                Long recentStaff = findRecentStaff(userId, shopId, rule.getRepeatWindowHours());
                if (recentStaff != null && sessionManager.isStaffOnline(recentStaff)) {
                    log.info("IM 规则命中回头客：shopId={}, ruleId={}, ruleName={}, 命中条件=窗口{}小时内最近接待客服={}在线, → 直接分配, userId={}",
                            shopId, rule.getId(), rule.getRuleName(), rule.getRepeatWindowHours(), recentStaff, userId);
                    return new DispatchResolveResult(rule, rule.getGroupId(), true, null,
                            Integer.valueOf(1).equals(activeRule.getRobotFirst()), recentStaff);
                }
            }
            // 条件匹配：入口意图 AND 订单状态（订单入口必须同时满足订单状态条件）
            MatchOutcome outcome = matches(rule, intent, orderStatus);
            if (outcome.hit()) {
                log.info("IM 规则命中：shopId={}, ruleId={}, ruleName={}, 命中条件={}, → groupId={}, intent={}, orderStatus={}, userId={}",
                        shopId, rule.getId(), rule.getRuleName(), outcome.reason(), rule.getGroupId(), intent, orderStatus, userId);
                return new DispatchResolveResult(rule, rule.getGroupId(), true, null,
                        Integer.valueOf(1).equals(activeRule.getRobotFirst()), null);
            }
        }
        if (activeRule == null) {
            // 全部启用规则均不在服务时间内 → 非服务时间模式
            log.info("IM 非服务时间模式：shopId={}", shopId);
            return new DispatchResolveResult(null, null, false,
                    fallbackTip != null ? fallbackTip : DEFAULT_OFF_HOURS_TIP, false, null);
        }
        // 有服务时间内的规则但条件未命中 → 基础分流
        log.info("IM 规则未命中，基础分流：shopId={}, intent={}, orderStatus={}", shopId, intent, orderStatus);
        return new DispatchResolveResult(null, null, true, null,
                Integer.valueOf(1).equals(activeRule.getRobotFirst()), null);
    }

    @Override
    public boolean inServiceTime(DispatchRule rule, LocalDateTime now) {
        // 空开始=全天
        if (rule.getServiceStart() == null || rule.getServiceStart().isBlank()) {
            return true;
        }
        if (rule.getServiceEnd() == null || rule.getServiceEnd().isBlank()) {
            return true;
        }
        int nowMin = now.getHour() * 60 + now.getMinute();
        int startMin = parseMinutes(rule.getServiceStart());
        int endMin = parseMinutes(rule.getServiceEnd());
        if (startMin < 0 || endMin < 0) {
            return true; // 配置非法时按全天处理
        }
        if (endMin <= startMin) {
            // 跨天（如 22:00-08:00）：start ≤ now < end（次日）
            return nowMin >= startMin || nowMin < endMin;
        }
        return nowMin >= startMin && nowMin < endMin;
    }

    private int parseMinutes(String hhmm) {
        try {
            String[] parts = hhmm.split(":");
            return Integer.parseInt(parts[0].trim()) * 60 + Integer.parseInt(parts[1].trim());
        } catch (Exception e) {
            return -1;
        }
    }

    /** 匹配结果：是否命中 + 可读命中原因（日志溯源：定位到规则的具体哪个条件） */
    private record MatchOutcome(boolean hit, String reason) {
    }

    /** 订单类入口意图标识（订单卡片/订单页跳转，需同时满足订单状态条件） */
    private static final String INTENT_ORDER = "order";

    /**
     * 条件匹配（AND 语义）：
     * 1) 入口意图为 order（订单入口）且规则配置了订单状态条件 → 订单状态必须同时满足才算命中；
     * 2) 非 order 入口（product/default）→ 意图命中即可，订单状态条件不参与约束；
     * 3) 规则未配置入口意图 → 仅按订单状态判定；配置了入口意图但未命中 → 不再命中（订单状态不能单独生效）；
     * 4) 两个维度都未配置 → 不参与自动匹配。
     */
    private MatchOutcome matches(DispatchRule rule, String intent, Integer orderStatus) {
        boolean intentConfigured = rule.getIntents() != null && !rule.getIntents().isBlank();
        boolean statusConfigured = rule.getOrderStatuses() != null && !rule.getOrderStatuses().isBlank();
        if (!intentConfigured && !statusConfigured) {
            return new MatchOutcome(false, null);
        }
        boolean intentHit = false;
        if (intentConfigured && intent != null) {
            for (String i : rule.getIntents().split(",")) {
                if (intent.equals(i.trim())) {
                    intentHit = true;
                    break;
                }
            }
        }
        boolean statusHit = false;
        if (statusConfigured && orderStatus != null) {
            for (String s : rule.getOrderStatuses().split(",")) {
                if (s.isBlank()) {
                    continue;
                }
                try {
                    if (orderStatus == Integer.parseInt(s.trim())) {
                        statusHit = true;
                        break;
                    }
                } catch (NumberFormatException ignored) {
                    // 配置了非法状态码，跳过
                }
            }
        }
        if (intentHit) {
            // 订单入口：配置了订单状态条件时必须同时满足（AND）
            if (INTENT_ORDER.equals(intent) && statusConfigured) {
                if (!statusHit) {
                    return new MatchOutcome(false, null);
                }
                return new MatchOutcome(true,
                        "入口意图+订单状态均命中: intent=" + intent + " ∈ 规则条件[" + rule.getIntents()
                                + "] 且 orderStatus=" + orderStatus + " ∈ 规则条件[" + rule.getOrderStatuses() + "]");
            }
            return new MatchOutcome(true,
                    "入口意图命中: intent=" + intent + " ∈ 规则条件[" + rule.getIntents() + "]");
        }
        // 未配置入口意图的规则才允许仅按订单状态命中；配置了意图但未命中 → 整条规则不命中
        if (!intentConfigured && statusHit) {
            return new MatchOutcome(true,
                    "订单状态命中: orderStatus=" + orderStatus + " ∈ 规则条件[" + rule.getOrderStatuses() + "]");
        }
        return new MatchOutcome(false, null);
    }

    /** 回头客判定：本店该用户最近一条非空 staff_id 的服务记录，updated_at 在窗口内 */
    private Long findRecentStaff(Long userId, Long shopId, Integer windowHours) {
        int hours = windowHours == null || windowHours <= 0 ? 24 : windowHours;
        ServiceRecord recent = serviceRecordMapper.selectOne(new LambdaQueryWrapper<ServiceRecord>()
                .eq(ServiceRecord::getShopId, shopId)
                .eq(ServiceRecord::getUserId, userId)
                .isNotNull(ServiceRecord::getStaffId)
                .ge(ServiceRecord::getUpdatedAt, LocalDateTime.now().minusHours(hours))
                .orderByDesc(ServiceRecord::getUpdatedAt)
                .last("limit 1"));
        return recent == null ? null : recent.getStaffId();
    }

    // ========== 队列 / 离线池 ==========

    @Override
    public void assignOrQueue(Conversation conv, Long groupId, Long repeatStaffId, boolean notify) {
        Long conversationId = conv.getId();
        Long shopId = conv.getShopId();
        // 回头客优先（仅要求在线；判定瞬间掉线则降级正常分流）
        if (repeatStaffId != null) {
            if (tryAssignConversation(conversationId, shopId, repeatStaffId)) {
                return;
            }
            log.info("IM 回头客客服已掉线，降级正常分流：conversationId={}, staffId={}", conversationId, repeatStaffId);
        }
        boolean assigned = tryAssignConversation(conversationId, shopId, null);
        if (assigned) {
            return;
        }
        if (groupId != null) {
            // 组内无可用：还有"在线且未挂起"客服（仅繁忙）→ 排队；否则 → 离线消息池
            if (hasOnlineAvailableStaff(groupId)) {
                enterQueue(reload(conversationId), notify);
            } else {
                enterOfflinePool(reload(conversationId), notify);
            }
        } else {
            // 基础分流无可用客服 → 离线消息池
            enterOfflinePool(reload(conversationId), notify);
        }
    }

    @Override
    public boolean tryAssignConversation(Long conversationId, Long shopId, Long repeatStaffId) {
        Conversation conv = conversationMapper.selectById(conversationId);
        if (conv == null || conv.getAssigneeId() != null) {
            log.info("IM 自动分配放弃（会话不存在或已被分配）：conversationId={}", conversationId);
            return false;
        }
        // 回头客分配：仅要求在线，无视挂起/最大接待数
        if (repeatStaffId != null) {
            if (!sessionManager.isStaffOnline(repeatStaffId)) {
                return false;
            }
            assign(conv, repeatStaffId, "回头客直接分配");
            return true;
        }
        Long dispatchGroupId = conv.getDispatchGroupId();
        Set<Long> onlineStaffIds = sessionManager.getOnlineStaffIds(shopId);
        if (onlineStaffIds.isEmpty()) {
            log.info("IM 无可分配客服：shopId={}, conversationId={}", shopId, conversationId);
            return false;
        }
        Map<Long, Integer> weightMap = Collections.emptyMap();
        Set<Long> candidateStaffIds;
        Map<Long, Long> servingCount = Collections.emptyMap();
        if (dispatchGroupId != null) {
            DispatchGroup group = dispatchGroupMapper.selectById(dispatchGroupId);
            weightMap = getGroupStaffWithWeight(dispatchGroupId);
            // 组内候选：组内客服 ∩ 在线 ∩ 未挂起
            candidateStaffIds = onlineStaffIds.stream()
                    .filter(weightMap::containsKey)
                    .filter(sid -> !sessionManager.isStaffSuspended(sid))
                    .collect(Collectors.toSet());
            if (candidateStaffIds.isEmpty()) {
                // 组内无人在线或全部挂起 → 无可用（调用方据此进离线池）
                log.info("IM 分流分组 {} 无人在线或全部挂起：shopId={}", dispatchGroupId, shopId);
                return false;
            }
            // 组内分配：排除已达该组 max_concurrent 的客服（按未结束服务数计）
            int max = group.getMaxConcurrent() == null || group.getMaxConcurrent() < 1 ? 5 : group.getMaxConcurrent();
            servingCount = countInProgressByStaff(candidateStaffIds);
            final Map<Long, Long> serving = servingCount;
            candidateStaffIds = candidateStaffIds.stream()
                    .filter(sid -> serving.getOrDefault(sid, 0L) < max)
                    .collect(Collectors.toSet());
            if (candidateStaffIds.isEmpty()) {
                // 组内在线未挂起但全部超载 → 排队（调用方据此进队列）
                log.info("IM 分流分组 {} 全部客服达最大接待数：shopId={}", dispatchGroupId, shopId);
                return false;
            }
        } else {
            // 基础分流：本店在线未挂起且有工作台权限的客服（无权限子账号时主账号兜底）
            candidateStaffIds = buildBaseCandidates(shopId);
            if (candidateStaffIds.isEmpty()) {
                log.info("IM 基础分流无可用客服：shopId={}", shopId);
                return false;
            }
            servingCount = countInProgressByStaff(candidateStaffIds);
        }

        // 加权选人：score = 未结束服务数 / 权重；并列随机（避免固定落在同一客服）
        List<Long> best = new ArrayList<>();
        double minScore = Double.MAX_VALUE;
        for (Long sid : candidateStaffIds) {
            long count = servingCount.getOrDefault(sid, 0L);
            int weight = Math.max(weightMap.getOrDefault(sid, 1), 1);
            double score = (double) count / weight;
            if (score < minScore) {
                minScore = score;
                best.clear();
                best.add(sid);
            } else if (Double.compare(score, minScore) == 0) {
                best.add(sid);
            }
        }
        if (best.isEmpty()) {
            return false;
        }
        Long assigneeId = best.size() == 1 ? best.get(0)
                : best.get(ThreadLocalRandom.current().nextInt(best.size()));
        log.info("IM 选人决策：conversationId={}, 方式={}, 候选客服={}, 负载(未结束服务数)={}, 权重={}, 最优候选={} → 选中 staffId={}",
                conversationId, dispatchGroupId != null ? "组内加权" : "基础分流均衡",
                candidateStaffIds, servingCount, weightMap, best, assigneeId);
        assign(conv, assigneeId, dispatchGroupId != null ? "组内加权选人" : "基础分流均衡分配");
        return true;
    }

    @Override
    public void enterQueue(Conversation conv, boolean notify) {
        // 仅正常状态会话可进入（已在队列/离线池则跳过，并发安全）
        int updated = conversationMapper.update(null, new LambdaUpdateWrapper<Conversation>()
                .eq(Conversation::getId, conv.getId())
                .isNull(Conversation::getDispatchStatus)
                .set(Conversation::getDispatchStatus, STATUS_QUEUEING)
                .set(Conversation::getDispatchAt, LocalDateTime.now()));
        if (updated > 0) {
            log.info("IM 会话进入排队队列：conversationId={}, shopId={}", conv.getId(), conv.getShopId());
            if (notify) {
                insertSystemMessage(conv, 0L, "系统", "queueing", "客服繁忙，请耐心等待，您已进入排队队列");
            }
        } else if (notify) {
            // 已在队列/离线池（如客服下线静默入池）：用户发消息路径按实际状态补提示
            notifyUnavailable(conv);
        }
    }

    @Override
    public void enterOfflinePool(Conversation conv, boolean notify) {
        // 仅正常状态会话可进入（已在队列/离线池则跳过，并发安全）
        int updated = conversationMapper.update(null, new LambdaUpdateWrapper<Conversation>()
                .eq(Conversation::getId, conv.getId())
                .isNull(Conversation::getDispatchStatus)
                .set(Conversation::getDispatchStatus, STATUS_OFFLINE_POOL)
                .set(Conversation::getDispatchAt, LocalDateTime.now()));
        if (updated > 0) {
            log.info("IM 会话进入离线消息池：conversationId={}, shopId={}", conv.getId(), conv.getShopId());
            if (notify) {
                insertSystemMessage(conv, 0L, "系统", "offline-pool", "当前暂无客服在线，您的消息稍后处理");
            }
        } else if (notify) {
            // 已在队列/离线池（如客服下线静默入池）：用户发消息路径按实际状态补提示
            notifyUnavailable(conv);
        }
    }

    /** 用户发消息但无客服可用时，按会话实际分流状态补发提示（客服下线释放路径不主动打扰用户） */
    private void notifyUnavailable(Conversation conv) {
        Conversation cur = conversationMapper.selectById(conv.getId());
        if (cur == null) {
            return;
        }
        if (STATUS_QUEUEING.equals(cur.getDispatchStatus())) {
            insertSystemMessage(conv, 0L, "系统", "queueing", "客服繁忙，请耐心等待，您已进入排队队列");
        } else if (STATUS_OFFLINE_POOL.equals(cur.getDispatchStatus())) {
            insertSystemMessage(conv, 0L, "系统", "offline-pool", "当前暂无客服在线，您的消息稍后处理");
        }
    }

    @Override
    public void claimOffline(Long conversationId, Long staffId, String staffName) {
        Conversation conv = conversationMapper.selectById(conversationId);
        if (conv == null) {
            throw new IllegalArgumentException("会话不存在: " + conversationId);
        }
        if (!STATUS_OFFLINE_POOL.equals(conv.getDispatchStatus())) {
            throw new BusinessException("该会话不在离线消息池中");
        }
        if (!sessionManager.isStaffOnline(staffId)) {
            throw new BusinessException("请先登录客服工作台后再认领");
        }
        // 认领即默认上线：挂起状态下认领会自动恢复接单（挂起只是不接新消息，认领代表主动接单）
        if (sessionManager.isStaffSuspended(staffId)) {
            sessionManager.setSuspended(staffId, false);
            log.info("IM 离线池认领自动恢复上线：staffId={}", staffId);
        }
        assign(conv, staffId, "离线池手动认领");
        log.info("IM 离线消息池认领：conversationId={}, staffId={}", conversationId, staffId);
    }

    @Override
    public int consumeQueue(Long shopId) {
        // 先检查服务时间：有启用规则且全不在服务时间内 → 不消费
        if (!canConsume(shopId)) {
            log.info("IM 队列消费跳过（非服务时间）：shopId={}", shopId);
            return 0;
        }
        // FIFO：按进入队列时间升序逐条尝试分配
        List<Conversation> queue = conversationMapper.selectList(
                new LambdaQueryWrapper<Conversation>()
                        .eq(Conversation::getShopId, shopId)
                        .eq(Conversation::getDispatchStatus, STATUS_QUEUEING)
                        .orderByAsc(Conversation::getDispatchAt)
                        .last("limit 20"));
        int consumed = 0;
        for (Conversation c : queue) {
            // 仅 FAQ 点击的会话（无真实用户消息）无需人工处理：静默出队，跳过分配
            if (!hasRealUserMessage(c.getId())) {
                exitDispatchState(c, STATUS_QUEUEING);
                continue;
            }
            if (tryAssignConversation(c.getId(), shopId, null)) {
                consumed++;
            }
        }
        if (consumed > 0) {
            log.info("IM 队列消费完成：shopId={}, consumed={}", shopId, consumed);
        }
        return consumed;
    }

    @Override
    public int consumeOfflinePool(Long shopId) {
        if (!canConsume(shopId)) {
            log.info("IM 离线池消费跳过（非服务时间）：shopId={}", shopId);
            return 0;
        }
        List<Conversation> pool = conversationMapper.selectList(
                new LambdaQueryWrapper<Conversation>()
                        .eq(Conversation::getShopId, shopId)
                        .eq(Conversation::getDispatchStatus, STATUS_OFFLINE_POOL)
                        .orderByAsc(Conversation::getDispatchAt)
                        .last("limit 20"));
        int consumed = 0;
        for (Conversation c : pool) {
            // 仅 FAQ 点击的会话（无真实用户消息）无需人工处理：静默出池，跳过分配
            if (!hasRealUserMessage(c.getId())) {
                exitDispatchState(c, STATUS_OFFLINE_POOL);
                continue;
            }
            // 按规则重新分流：以会话内最近消息类型推断入口意图（订单状态无法离线反查，仅按意图）
            String intent = switch (c.getLastMessageType() == null ? "" : c.getLastMessageType()) {
                case "product_card" -> "product";
                case "order_card" -> "order";
                default -> "default";
            };
            DispatchResolveResult resolve = resolveDispatchRule(intent, null, c.getUserId(), shopId);
            if (!resolve.isInServiceTime()) {
                continue;
            }
            if (resolve.getGroupId() != null && !resolve.getGroupId().equals(c.getDispatchGroupId())) {
                c.setDispatchGroupId(resolve.getGroupId());
                conversationMapper.updateById(c);
            }
            if (tryAssignConversation(c.getId(), shopId, resolve.getRepeatStaffId())) {
                consumed++;
            }
        }
        if (consumed > 0) {
            log.info("IM 离线池消费完成：shopId={}, consumed={}", shopId, consumed);
        }
        return consumed;
    }

    /**
     * 会话是否存在真实用户消息（非 FAQ 引导点击）：
     * 仅点引导问题的会话机器人已处理，无需人工接待，不应占用队列/离线池资源被分配给客服。
     * 商品/订单卡片、输入的问题均算真实消息（extra.faqClick 非 true）。
     */
    private boolean hasRealUserMessage(Long conversationId) {
        long count = mongoTemplate.count(new Query(
                Criteria.where("conversationId").is(conversationId)
                        .and("senderRole").is("user")
                        .and("extra.faqClick").ne(true)),
                ImMessage.class);
        return count > 0;
    }

    /** 无需人工处理的队列/离线池会话静默放出回正常状态（按分流状态 CAS，防并发重复处理） */
    private void exitDispatchState(Conversation conv, String expectStatus) {
        conversationMapper.update(null, new LambdaUpdateWrapper<Conversation>()
                .eq(Conversation::getId, conv.getId())
                .eq(Conversation::getDispatchStatus, expectStatus)
                .set(Conversation::getDispatchStatus, null)
                .set(Conversation::getDispatchAt, null));
        log.info("IM 会话无真实用户消息（仅 FAQ 点击），跳过分配并放出：conversationId={}, from={}",
                conv.getId(), expectStatus);
    }

    /** 服务时间检查：有启用规则且全不在服务时间内 → false（非服务时间不消费队列/离线池） */
    private boolean canConsume(Long shopId) {
        List<DispatchRule> rules = dispatchRuleMapper.selectList(
                new LambdaQueryWrapper<DispatchRule>()
                        .eq(DispatchRule::getShopId, shopId)
                        .eq(DispatchRule::getEnabled, 1));
        if (rules.isEmpty()) {
            return true;
        }
        LocalDateTime now = LocalDateTime.now();
        for (DispatchRule rule : rules) {
            if (inServiceTime(rule, now)) {
                return true;
            }
        }
        return false;
    }

    // ========== 统计 ==========

    @Override
    public DispatchStats stats(Long shopId) {
        DispatchStats stats = new DispatchStats();
        // 排队
        List<Conversation> queue = conversationMapper.selectList(
                new LambdaQueryWrapper<Conversation>()
                        .eq(Conversation::getShopId, shopId)
                        .eq(Conversation::getDispatchStatus, STATUS_QUEUEING));
        stats.setQueueTotal(queue.size());
        stats.setQueueByGroup(queue.stream()
                .filter(c -> c.getDispatchGroupId() != null)
                .collect(Collectors.groupingBy(Conversation::getDispatchGroupId, Collectors.counting())));
        // 离线池
        stats.setOfflinePoolTotal(conversationMapper.selectCount(
                new LambdaQueryWrapper<Conversation>()
                        .eq(Conversation::getShopId, shopId)
                        .eq(Conversation::getDispatchStatus, STATUS_OFFLINE_POOL)));
        // 在线/挂起（总数 + 按组）
        Set<Long> online = sessionManager.getOnlineStaffIds(shopId);
        // 排除主账号：主账号仅作为无工作台权限子账号时的兜底，不应计入在线客服统计
        try {
            R<MerchantAccountDTO> ownerResp = shopFeignClient.getShopOwner(shopId);
            if (ownerResp != null && ownerResp.isSuccess() && ownerResp.getData() != null) {
                online.remove(ownerResp.getData().getId());
            }
        } catch (Exception e) {
            log.warn("IM 统计查询主账号失败：shopId={}", shopId, e);
        }
        // 挂起 = 在线且挂起（继承主账号排除）；在线 = 在线且未挂起（挂起不接新消息，不应计入可接待数）
        Set<Long> suspended = online.stream()
                .filter(sessionManager::isStaffSuspended)
                .collect(Collectors.toSet());
        online.removeAll(suspended);
        stats.setOnlineTotal(online.size());
        stats.setSuspendedTotal(suspended.size());
        Map<Long, Long> onlineByGroup = new HashMap<>();
        Map<Long, Long> suspendedByGroup = new HashMap<>();
        for (DispatchGroup group : listByShop(shopId)) {
            Map<Long, Integer> staffWeights = getGroupStaffWithWeight(group.getId());
            long onlineCount = staffWeights.keySet().stream().filter(online::contains).count();
            long suspendedCount = staffWeights.keySet().stream().filter(suspended::contains).count();
            if (onlineCount > 0) {
                onlineByGroup.put(group.getId(), onlineCount);
            }
            if (suspendedCount > 0) {
                suspendedByGroup.put(group.getId(), suspendedCount);
            }
        }
        stats.setOnlineByGroup(onlineByGroup);
        stats.setSuspendedByGroup(suspendedByGroup);
        return stats;
    }

    @Override
    public Map<Long, Integer> getGroupStaffWithWeight(Long groupId) {
        Map<Long, Integer> map = new LinkedHashMap<>();
        for (DispatchGroupStaff s : dispatchGroupStaffMapper.selectList(
                new LambdaQueryWrapper<DispatchGroupStaff>()
                        .eq(DispatchGroupStaff::getGroupId, groupId))) {
            map.put(s.getStaffId(), s.getWeight() == null || s.getWeight() < 1 ? 1 : s.getWeight());
        }
        return map;
    }

    // ========== 私有方法 ==========

    /** 分配落地：更新接待客服 + 清分流状态 + 广播 assign 系统消息 + 指定服务最终处理人 */
    private void assign(Conversation conv, Long staffId, String reason) {
        String assigneeName = resolveAssigneeName(staffId, String.valueOf(staffId));
        conversationMapper.update(null, new LambdaUpdateWrapper<Conversation>()
                .eq(Conversation::getId, conv.getId())
                .set(Conversation::getAssigneeId, staffId)
                .set(Conversation::getAssigneeName, assigneeName)
                .set(Conversation::getDispatchStatus, null)
                .set(Conversation::getDispatchAt, null));
        log.info("IM 自动分配完成：conversationId={}, shopId={}, dispatchGroupId={}, assigneeId={}, assigneeName={}, 分配原因={}",
                conv.getId(), conv.getShopId(), conv.getDispatchGroupId(), staffId, assigneeName, reason);
        insertSystemMessage(conv, staffId, assigneeName, "assign", "客服 " + assigneeName + " 已接入聊天");
        // 服务记录：确保进行中服务存在并指定最终处理人（服务开始）
        serviceRecordService.updateFinalStaff(conv.getId(), staffId, assigneeName);
    }

    /** 基础分流候选：本店在线未挂起且有工作台权限的子账号；本店无权限子账号时主账号纳入候选 */
    private Set<Long> buildBaseCandidates(Long shopId) {
        Set<Long> candidates = new HashSet<>();
        try {
            R<List<Long>> resp = rbacFeignClient.listUserIdsByPerm(PERM_IM_WORKBENCH, USER_TYPE_MERCHANT);
            if (resp != null && resp.isSuccess() && resp.getData() != null && !resp.getData().isEmpty()) {
                Map<Long, MerchantAccountDTO> accounts = fetchAccounts(resp.getData());
                for (Long sid : resp.getData()) {
                    MerchantAccountDTO acc = accounts.get(sid);
                    // 仅本店子账号（parentId 非空）
                    if (acc != null && shopId.equals(acc.getShopId()) && acc.getParentId() != null) {
                        candidates.add(sid);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("IM 查询工作台权限用户失败，降级主账号候选：shopId={}", shopId, e);
        }
        // 本店不存在任何有工作台权限的子账号 → 主账号纳入候选（需求8）
        if (candidates.isEmpty()) {
            try {
                R<MerchantAccountDTO> ownerResp = shopFeignClient.getShopOwner(shopId);
                if (ownerResp != null && ownerResp.isSuccess() && ownerResp.getData() != null) {
                    candidates.add(ownerResp.getData().getId());
                }
            } catch (Exception e) {
                log.warn("IM 查询店铺主账号失败：shopId={}", shopId, e);
            }
        }
        // 过滤：在线 且 未挂起
        candidates.removeIf(sid -> !sessionManager.isStaffOnline(sid) || sessionManager.isStaffSuspended(sid));
        return candidates;
    }

    /** 批量反查商家账号（parentId/shopId 判定用），失败降级空 Map */
    private Map<Long, MerchantAccountDTO> fetchAccounts(List<Long> ids) {
        try {
            R<List<MerchantAccountDTO>> resp = shopFeignClient.getMerchantsByIds(ids);
            if (resp != null && resp.isSuccess() && resp.getData() != null) {
                return resp.getData().stream()
                        .collect(Collectors.toMap(MerchantAccountDTO::getId, a -> a, (a, b) -> a));
            }
        } catch (Exception e) {
            log.warn("IM 批量查询商家账号失败：ids={}", ids, e);
        }
        return Collections.emptyMap();
    }

    /** 统计候选客服未结束（IN_PROGRESS）的服务数 */
    private Map<Long, Long> countInProgressByStaff(Set<Long> staffIds) {
        if (staffIds == null || staffIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return serviceRecordMapper.selectList(new LambdaQueryWrapper<ServiceRecord>()
                        .in(ServiceRecord::getStaffId, staffIds)
                        .eq(ServiceRecord::getStatus, ServiceRecordService.STATUS_IN_PROGRESS))
                .stream()
                .filter(r -> r.getStaffId() != null)
                .collect(Collectors.groupingBy(ServiceRecord::getStaffId, Collectors.counting()));
    }

    /** 组内是否还有"在线且未挂起"的客服（仅繁忙 → 排队；否则 → 离线池） */
    private boolean hasOnlineAvailableStaff(Long groupId) {
        for (Long staffId : getGroupStaffWithWeight(groupId).keySet()) {
            if (sessionManager.isStaffOnline(staffId) && !sessionManager.isStaffSuspended(staffId)) {
                return true;
            }
        }
        return false;
    }

    /** 租户校验：分组存在且属于本店 */
    private DispatchGroup requireOwnedGroup(Long groupId, Long shopId) {
        if (groupId == null || shopId == null) {
            throw new BusinessException("分组不能为空");
        }
        DispatchGroup group = dispatchGroupMapper.selectById(groupId);
        if (group == null || !shopId.equals(group.getShopId())) {
            throw new BusinessException("分组不存在");
        }
        return group;
    }

    private Conversation reload(Long conversationId) {
        return conversationMapper.selectById(conversationId);
    }

    /** 获取客服展示姓名：真实姓名优先，兜底登录名/ID */
    private String resolveAssigneeName(Long staffId, String fallback) {
        try {
            R<MerchantAccountDTO> resp = shopFeignClient.getMerchantById(staffId);
            if (resp != null && resp.isSuccess() && resp.getData() != null) {
                String name = resp.getData().getRealName();
                if (name != null && !name.isBlank()) {
                    return name;
                }
            }
        } catch (Exception e) {
            log.warn("获取客服姓名失败：staffId={}", staffId, e);
        }
        return fallback != null && !fallback.isBlank() ? fallback : String.valueOf(staffId);
    }

    /** 插入指定类型系统消息并广播（senderId 为最新接待者，商家端据此同步会话归属） */
    private void insertSystemMessage(Conversation conversation, Long senderId, String senderName,
                                     String systemType, String content) {
        ImMessage sysMsg = new ImMessage();
        sysMsg.setConversationId(conversation.getId());
        sysMsg.setSenderId(senderId);
        sysMsg.setSenderRole("merchant");
        sysMsg.setShopId(conversation.getShopId());
        sysMsg.setUserId(conversation.getUserId());
        sysMsg.setType("text");
        sysMsg.setContent(content);
        sysMsg.setSenderName(senderName);
        sysMsg.setSystemType(systemType);
        sysMsg.setRead(true);
        sysMsg.setCreatedAt(LocalDateTime.now());
        sysMsg = mongoTemplate.save(sysMsg);
        MessageView view = toView(sysMsg);
        imEventProducer.broadcast(new ImBroadcast("message", conversation.getUserId(), conversation.getShopId(), view));
    }

    private MessageView toView(ImMessage d) {
        MessageView v = new MessageView();
        v.setId(d.getId());
        v.setConversationId(d.getConversationId());
        v.setSenderId(d.getSenderId());
        v.setSenderRole(d.getSenderRole());
        v.setShopId(d.getShopId());
        v.setUserId(d.getUserId());
        v.setType(d.getType());
        v.setContent(d.getContent());
        v.setSenderName(d.getSenderName());
        v.setQuoteId(d.getQuoteId());
        v.setQuoteContent(d.getQuoteContent());
        v.setQuoteSenderName(d.getQuoteSenderName());
        v.setRecalled(d.getRecalled());
        v.setSystemType(d.getSystemType());
        v.setExtra(d.getExtra());
        v.setRead(d.getRead());
        v.setCreatedAt(d.getCreatedAt());
        return v;
    }
}
