package com.byw.im.service;

import com.byw.im.dto.DispatchResolveResult;
import com.byw.im.dto.DispatchStats;
import com.byw.im.entity.Conversation;
import com.byw.im.entity.DispatchGroup;
import com.byw.im.entity.DispatchGroupStaff;
import com.byw.im.entity.DispatchRule;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * IM 客服分流服务：
 * <ul>
 *   <li>分组 = 职能（只表达哪组客服处理哪块问题），不承担匹配条件/优先级/默认兜底</li>
 *   <li>规则 = 分流策略：条件（意图/订单状态）命中后消息落到绑定分组，组内按客服权重均衡分配</li>
 *   <li>队列/离线池：组内繁忙 → QUEUEING；无人可接 → OFFLINE_POOL；触发点消费</li>
 * </ul>
 */
public interface DispatchService {

    /** 分流状态：排队中 */
    String STATUS_QUEUEING = "QUEUEING";

    /** 分流状态：离线消息池 */
    String STATUS_OFFLINE_POOL = "OFFLINE_POOL";

    // ========== 分流分组 CRUD ==========

    /** 分流分组列表（含禁用；填充组内客服数 staffCount） */
    List<DispatchGroup> listByShop(Long shopId);

    /** 新增分组 */
    DispatchGroup create(DispatchGroup group);

    /** 更新分组（校验属于本店） */
    DispatchGroup update(DispatchGroup group);

    /** 删除分组（含客服关联；被启用规则引用时拒绝） */
    void delete(Long id, Long shopId);

    /** 获取员工所属分流分组关联（含权重） */
    List<DispatchGroupStaff> getStaffGroups(Long staffId);

    /** 保存员工分流分组（先删后插，含权重；同一客服只能在一个分组，多于一个拒绝） */
    void saveStaffGroups(Long staffId, Map<Long, Integer> groupWeights);

    /** 保存分组内客服（先删后插，含权重；分组必须属于本店；客服已属其他分组时拒绝） */
    void saveGroupStaff(Long groupId, Long shopId, Map<Long, Integer> staffWeights);

    /** 本店客服分组归属 map（staffId -> groupId，分组弹窗禁用已属他组客服用） */
    Map<Long, Long> getStaffGroupMap(Long shopId);

    // ========== 分流规则 CRUD ==========

    /** 规则列表（含禁用，按 priority 升序；返回分组名快照） */
    List<DispatchRule> listRules(Long shopId);

    /** 新增规则（校验 group_id 为本店分组） */
    DispatchRule createRule(DispatchRule rule);

    /** 更新规则（校验 group_id 为本店分组） */
    DispatchRule updateRule(DispatchRule rule);

    /** 删除规则（直接删） */
    void deleteRule(Long id, Long shopId);

    /** 启用/停用规则 */
    void toggleRule(Long id, Long shopId, boolean enabled);

    // ========== 规则解析 ==========

    /**
     * 分流决策解析：按优先级升序遍历启用规则，输出决策所需全部信息。
     * 全部启用规则均不在服务时间内 → 非服务时间模式（inServiceTime=false）。
     * 有服务时间内的规则但条件未命中 → 基础分流（rule/groupId=null，inServiceTime=true）。
     * 无启用规则 → 全天候基础分流。
     */
    DispatchResolveResult resolveDispatchRule(String intent, Integer orderStatus, Long userId, Long shopId);

    /** 规则服务时间判定：空时间段=全天；serviceEnd &lt; serviceStart 视为跨天 */
    boolean inServiceTime(DispatchRule rule, LocalDateTime now);

    // ========== 队列 / 离线池 ==========

    /**
     * 分流落地：尝试分配（回头客直分/组内权重/基础分流），失败时按组内情况进入排队队列或离线消息池。
     * @param repeatStaffId 回头客客服（仅要求在线，无视挂起/最大接待数）；null=不启用回头客
     * @param notify 是否向用户广播排队/离线池系统提示：用户发消息路径=true；
     *               客服下线释放路径=false（用户未发新消息时不主动打扰）
     */
    void assignOrQueue(Conversation conv, Long groupId, Long repeatStaffId, boolean notify);

    /**
     * 核心选人分配：按会话当前 dispatch_group_id 组内加权分配；无分组时全店均衡分配。
     * 组内分配排除已达 max_concurrent 的客服；基础分流不校验超载。
     * 分配成功：更新接待客服 + 清空 dispatch_status/dispatch_at + 广播 assign 系统消息 + 指定服务最终处理人。
     * @return 是否分配成功
     */
    boolean tryAssignConversation(Long conversationId, Long shopId, Long repeatStaffId);

    /** 会话进入排队队列（QUEUEING + dispatch_at，幂等：已在队列/离线池则跳过）；notify=true 时向用户提示（含已在池内时按实际状态补提示） */
    void enterQueue(Conversation conv, boolean notify);

    /** 会话进入离线消息池（OFFLINE_POOL + dispatch_at，幂等：已在队列/离线池则跳过）；notify=true 时向用户提示（含已在池内时按实际状态补提示） */
    void enterOfflinePool(Conversation conv, boolean notify);

    /** 认领离线池会话：assignee 置为认领客服 + 清分流状态 + 广播 assign 系统消息 + 指定服务最终处理人 */
    void claimOffline(Long conversationId, Long staffId, String staffName);

    /** 消费排队队列：FIFO（按 dispatch_at 升序）逐条尝试分配；先检查服务时间（有启用规则且全不在服务时间内则不消费） */
    int consumeQueue(Long shopId);

    /** 消费离线消息池：按规则重新分流逐条尝试分配；先检查服务时间 */
    int consumeOfflinePool(Long shopId);

    // ========== 统计 ==========

    /** 分流统计：排队/在线/挂起（总数+按组）、离线池总数 */
    DispatchStats stats(Long shopId);

    /** 获取分组内客服及接待权重（staffId → weight） */
    Map<Long, Integer> getGroupStaffWithWeight(Long groupId);
}
