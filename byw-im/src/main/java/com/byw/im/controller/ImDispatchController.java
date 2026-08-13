package com.byw.im.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.byw.api.user.UserFeignClient;
import com.byw.api.user.dto.UserDTO;
import com.byw.common.core.result.PageResult;
import com.byw.common.core.result.R;
import com.byw.common.security.annotation.RequireLogin;
import com.byw.common.security.context.UserContext;
import com.byw.im.dto.DispatchStats;
import com.byw.im.entity.Conversation;
import com.byw.im.entity.DispatchGroup;
import com.byw.im.entity.DispatchGroupStaff;
import com.byw.im.entity.DispatchRule;
import com.byw.im.mapper.ConversationMapper;
import com.byw.im.service.DispatchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * IM 客服分流管理 REST 接口（商家端）：
 * 分流分组（职能组）/ 分流规则（策略）/ 离线消息池 / 统计。
 */
@Tag(name = "客服分流")
@RestController
@RequestMapping("/im/dispatch")
@RequiredArgsConstructor
public class ImDispatchController {

    /** 订单状态选项（与 byw-order 状态码一致），规则匹配条件下拉用 */
    private static final Map<Integer, String> ORDER_STATUS_OPTIONS = new LinkedHashMap<>(Map.ofEntries(
            Map.entry(0, "待付款"),
            Map.entry(1, "待发货"),
            Map.entry(2, "待收货"),
            Map.entry(3, "交易完成"),
            Map.entry(4, "交易关闭"),
            Map.entry(5, "退款中"),
            Map.entry(7, "部分发货")));

    private final DispatchService dispatchService;
    private final ConversationMapper conversationMapper;
    private final UserFeignClient userFeignClient;

    // ========== 分流分组（职能组） ==========

    @Operation(summary = "分流分组列表（含禁用，填充组内客服数）")
    @RequireLogin
    @GetMapping("/group/list")
    public R<List<DispatchGroup>> groupList() {
        Long shopId = UserContext.getShopId();
        if (shopId == null) return R.fail("仅商家端可用");
        return R.ok(dispatchService.listByShop(shopId));
    }

    @Operation(summary = "新增分流分组")
    @RequireLogin
    @PostMapping("/group")
    public R<DispatchGroup> groupCreate(@RequestBody DispatchGroup group) {
        Long shopId = UserContext.getShopId();
        if (shopId == null) return R.fail("仅商家端可用");
        group.setId(null);
        group.setShopId(shopId);
        return R.ok(dispatchService.create(group));
    }

    @Operation(summary = "更新分流分组")
    @RequireLogin
    @PutMapping("/group")
    public R<DispatchGroup> groupUpdate(@RequestBody DispatchGroup group) {
        Long shopId = UserContext.getShopId();
        if (shopId == null) return R.fail("仅商家端可用");
        group.setShopId(shopId);
        return R.ok(dispatchService.update(group));
    }

    @Operation(summary = "删除分流分组（被启用规则引用时拒绝）")
    @RequireLogin
    @DeleteMapping("/group/{id}")
    public R<Void> groupDelete(@PathVariable Long id) {
        Long shopId = UserContext.getShopId();
        if (shopId == null) return R.fail("仅商家端可用");
        dispatchService.delete(id, shopId);
        return R.ok();
    }

    // ========== 分流规则 ==========

    @Operation(summary = "分流规则列表（含禁用，按优先级升序，返回分组名快照）")
    @RequireLogin
    @GetMapping("/rule/list")
    public R<List<DispatchRule>> ruleList() {
        Long shopId = UserContext.getShopId();
        if (shopId == null) return R.fail("仅商家端可用");
        return R.ok(dispatchService.listRules(shopId));
    }

    @Operation(summary = "新增分流规则")
    @RequireLogin
    @PostMapping("/rule")
    public R<DispatchRule> ruleCreate(@RequestBody DispatchRule rule) {
        Long shopId = UserContext.getShopId();
        if (shopId == null) return R.fail("仅商家端可用");
        rule.setId(null);
        rule.setShopId(shopId);
        return R.ok(dispatchService.createRule(rule));
    }

    @Operation(summary = "更新分流规则")
    @RequireLogin
    @PutMapping("/rule")
    public R<DispatchRule> ruleUpdate(@RequestBody DispatchRule rule) {
        Long shopId = UserContext.getShopId();
        if (shopId == null) return R.fail("仅商家端可用");
        rule.setShopId(shopId);
        return R.ok(dispatchService.updateRule(rule));
    }

    @Operation(summary = "删除分流规则")
    @RequireLogin
    @DeleteMapping("/rule/{id}")
    public R<Void> ruleDelete(@PathVariable Long id) {
        Long shopId = UserContext.getShopId();
        if (shopId == null) return R.fail("仅商家端可用");
        dispatchService.deleteRule(id, shopId);
        return R.ok();
    }

    @Operation(summary = "启用/停用分流规则（启用后立即消费队列/离线池）")
    @RequireLogin
    @PutMapping("/rule/{id}/status")
    public R<Void> ruleStatus(@PathVariable Long id, @RequestParam Boolean enabled) {
        Long shopId = UserContext.getShopId();
        if (shopId == null) return R.fail("仅商家端可用");
        dispatchService.toggleRule(id, shopId, Boolean.TRUE.equals(enabled));
        // 启用规则可能让排队/离线池会话命中新的分流 → 立即消费
        if (Boolean.TRUE.equals(enabled)) {
            dispatchService.consumeQueue(shopId);
            dispatchService.consumeOfflinePool(shopId);
        }
        return R.ok();
    }

    // ========== 离线消息池 ==========

    @Operation(summary = "离线消息池分页（含用户昵称/意图/消息摘要）")
    @RequireLogin
    @GetMapping("/offline-pool")
    public R<PageResult<OfflinePoolItem>> offlinePool(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        Long shopId = UserContext.getShopId();
        if (shopId == null) return R.fail("仅商家端可用");
        Page<Conversation> page = conversationMapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<Conversation>()
                        .eq(Conversation::getShopId, shopId)
                        .eq(Conversation::getDispatchStatus, DispatchService.STATUS_OFFLINE_POOL)
                        .orderByAsc(Conversation::getDispatchAt));
        List<Conversation> records = page.getRecords();
        // 批量反查买家昵称（失败降级为空 Map，前端回退"用户{userId}"）
        Map<Long, String> nicknameMap = resolveUserNicknames(records.stream()
                .map(Conversation::getUserId).collect(Collectors.toList()));
        // 批量反查规则命中的分流分组名（认领时客服可判断是否属于自己能处理的组）
        Map<Long, String> groupNameMap = resolveGroupNames(records.stream()
                .map(Conversation::getDispatchGroupId).filter(Objects::nonNull).distinct().collect(Collectors.toList()));
        List<OfflinePoolItem> items = records.stream().map(c -> {
            OfflinePoolItem item = new OfflinePoolItem();
            item.setConversationId(c.getId());
            item.setUserId(c.getUserId());
            item.setUserNickname(nicknameMap.get(c.getUserId()));
            item.setGroupId(c.getDispatchGroupId());
            item.setGroupName(c.getDispatchGroupId() == null ? null : groupNameMap.get(c.getDispatchGroupId()));
            item.setIntent(switch (c.getLastMessageType() == null ? "" : c.getLastMessageType()) {
                case "product_card" -> "product";
                case "order_card" -> "order";
                default -> "default";
            });
            item.setLastMessage(c.getLastMessage());
            item.setLastMessageType(c.getLastMessageType());
            item.setDispatchAt(c.getDispatchAt());
            return item;
        }).collect(Collectors.toList());
        return R.ok(PageResult.of(items, page.getTotal(), pageNum, pageSize));
    }

    @Operation(summary = "认领离线消息池会话")
    @RequireLogin
    @PostMapping("/offline-pool/{conversationId}/claim")
    public R<Void> claim(@PathVariable Long conversationId) {
        Long shopId = UserContext.getShopId();
        if (shopId == null) return R.fail("仅商家端可用");
        dispatchService.claimOffline(conversationId, UserContext.getUserId(), UserContext.getUsername());
        return R.ok();
    }

    // ========== 统计 ==========

    @Operation(summary = "分流统计（排队/在线/挂起/离线池，总数+按组）")
    @RequireLogin
    @GetMapping("/stats")
    public R<DispatchStats> stats() {
        Long shopId = UserContext.getShopId();
        if (shopId == null) return R.fail("仅商家端可用");
        return R.ok(dispatchService.stats(shopId));
    }

    @Operation(summary = "订单状态选项（规则匹配条件下拉用）")
    @RequireLogin
    @GetMapping("/order-status-options")
    public R<List<Map<String, Object>>> orderStatusOptions() {
        List<Map<String, Object>> options = ORDER_STATUS_OPTIONS.entrySet().stream().map(e -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("value", e.getKey());
            m.put("label", e.getValue());
            return m;
        }).collect(Collectors.toList());
        return R.ok(options);
    }

    // ========== 分组内客服 / 员工分流分组（员工管理弹窗用） ==========

    @Operation(summary = "获取员工分流分组（含权重）")
    @RequireLogin
    @GetMapping("/staff/{staffId}")
    public R<List<StaffGroupWeight>> staffGroups(@PathVariable Long staffId) {
        return R.ok(dispatchService.getStaffGroups(staffId).stream().map(s -> {
            StaffGroupWeight w = new StaffGroupWeight();
            w.setGroupId(s.getGroupId());
            w.setWeight(s.getWeight() == null ? 1 : s.getWeight());
            return w;
        }).collect(Collectors.toList()));
    }

    @Operation(summary = "客服分组归属 map（staffId -> groupId，分组弹窗禁用已属他组客服用）")
    @RequireLogin
    @GetMapping("/staff-group-map")
    public R<Map<Long, Long>> staffGroupMap() {
        Long shopId = UserContext.getShopId();
        if (shopId == null) return R.fail("仅商家端可用");
        return R.ok(dispatchService.getStaffGroupMap(shopId));
    }

    @Operation(summary = "保存员工分流分组（先删后插，含权重）")
    @RequireLogin
    @PostMapping("/staff")
    public R<Void> saveStaffGroups(@RequestBody SaveStaffGroupsRequest request) {
        Map<Long, Integer> weights = new LinkedHashMap<>();
        if (request.getItems() != null) {
            for (StaffGroupWeight item : request.getItems()) {
                if (item.getGroupId() != null) {
                    weights.put(item.getGroupId(), item.getWeight());
                }
            }
        }
        dispatchService.saveStaffGroups(request.getStaffId(), weights);
        return R.ok();
    }

    @Operation(summary = "获取分组内客服（含权重）")
    @RequireLogin
    @GetMapping("/{id}/staff")
    public R<List<GroupStaffWeight>> groupStaff(@PathVariable Long id) {
        return R.ok(dispatchService.getGroupStaffWithWeight(id).entrySet().stream().map(e -> {
            GroupStaffWeight w = new GroupStaffWeight();
            w.setStaffId(e.getKey());
            w.setWeight(e.getValue());
            return w;
        }).collect(Collectors.toList()));
    }

    @Operation(summary = "保存分组内客服（先删后插，含权重）")
    @RequireLogin
    @PostMapping("/{id}/staff")
    public R<Void> saveGroupStaff(@PathVariable Long id, @RequestBody SaveGroupStaffRequest request) {
        Long shopId = UserContext.getShopId();
        if (shopId == null) return R.fail("仅商家端可用");
        Map<Long, Integer> weights = new LinkedHashMap<>();
        if (request.getItems() != null) {
            for (GroupStaffWeight item : request.getItems()) {
                if (item.getStaffId() != null) {
                    weights.put(item.getStaffId(), item.getWeight());
                }
            }
        }
        dispatchService.saveGroupStaff(id, shopId, weights);
        return R.ok();
    }

    /** 批量反查买家昵称，失败降级为空 Map。 */
    private Map<Long, String> resolveUserNicknames(List<Long> userIds) {
        List<Long> distinct = userIds.stream().filter(Objects::nonNull).distinct().collect(Collectors.toList());
        if (distinct.isEmpty()) {
            return Collections.emptyMap();
        }
        try {
            R<List<UserDTO>> resp = userFeignClient.getUsersByIds(distinct);
            if (resp != null && resp.isSuccess() && resp.getData() != null) {
                Map<Long, String> map = new HashMap<>();
                for (UserDTO user : resp.getData()) {
                    String nick = user.getNickname();
                    if (nick != null && !nick.isBlank()) {
                        map.put(user.getId(), nick);
                    }
                }
                return map;
            }
        } catch (Exception e) {
            // 反查失败降级为空 Map
        }
        return Collections.emptyMap();
    }

    /** 分流分组 ID → 分组名（本店分组量少，直接取全量列表构建） */
    private Map<Long, String> resolveGroupNames(List<Long> groupIds) {
        if (groupIds == null || groupIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, String> map = new HashMap<>();
        for (DispatchGroup g : dispatchService.listByShop(UserContext.getShopId())) {
            map.put(g.getId(), g.getGroupName());
        }
        return map;
    }

    @Data
    public static class OfflinePoolItem {
        private Long conversationId;
        private Long userId;
        private String userNickname;
        private String intent;
        /** 规则命中的分流分组（基础分流入池时为 null） */
        private Long groupId;
        private String groupName;
        private String lastMessage;
        private String lastMessageType;
        private java.time.LocalDateTime dispatchAt;
    }

    @Data
    public static class StaffGroupWeight {
        private Long groupId;
        private Integer weight;
    }

    @Data
    public static class SaveStaffGroupsRequest {
        private Long staffId;
        private List<StaffGroupWeight> items;
    }

    @Data
    public static class GroupStaffWeight {
        private Long staffId;
        private Integer weight;
    }

    @Data
    public static class SaveGroupStaffRequest {
        private List<GroupStaffWeight> items;
    }
}
