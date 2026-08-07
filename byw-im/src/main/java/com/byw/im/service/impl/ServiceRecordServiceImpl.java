package com.byw.im.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.byw.im.document.ImMessage;
import com.byw.im.dto.ImBroadcast;
import com.byw.im.dto.MessageView;
import com.byw.im.entity.Conversation;
import com.byw.im.entity.ServiceRecord;
import com.byw.im.mapper.ConversationMapper;
import com.byw.im.mapper.ServiceRecordMapper;
import com.byw.im.producer.ImEventProducer;
import com.byw.im.service.SatisfactionService;
import com.byw.im.service.ServiceRecordService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * IM 服务记录实现：服务生命周期维护 + 超时自动结束 + 评价能力。
 * 系统消息直接落库广播（不依赖 ImService，避免与 ImServiceImpl 循环依赖）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ServiceRecordServiceImpl extends ServiceImpl<ServiceRecordMapper, ServiceRecord>
        implements ServiceRecordService {

    private final ServiceRecordMapper serviceRecordMapper;
    private final ConversationMapper conversationMapper;
    private final MongoTemplate mongoTemplate;
    private final ImEventProducer imEventProducer;

    @Override
    @Transactional
    public ServiceRecord touchActive(Long conversationId, Long staffId, String staffName) {
        Conversation conv = conversationMapper.selectById(conversationId);
        if (conv == null) {
            log.warn("IM 服务记录跳过（会话不存在）：conversationId={}", conversationId);
            return null;
        }
        ServiceRecord active = findActive(conversationId);
        LocalDateTime now = LocalDateTime.now();
        if (active == null) {
            active = new ServiceRecord();
            active.setConversationId(conversationId);
            active.setShopId(conv.getShopId());
            active.setUserId(conv.getUserId());
            active.setStatus(STATUS_IN_PROGRESS);
            active.setStartedAt(now);
            active.setLastMessageTime(now);
            active.setNotifiedBeforeEnd(0);
            resolveStaff(active, conv, staffId, staffName);
            serviceRecordMapper.insert(active);
            log.info("IM 服务开始：conversationId={}, staffId={}", conversationId, active.getStaffId());
            return active;
        }
        // 已有进行中服务：仅当最终处理人空缺时填充（转接/掉线重分配用 updateFinalStaff 强制更新）
        if (active.getStaffId() == null && staffId != null) {
            active.setStaffId(staffId);
            active.setStaffName(staffName);
        }
        active.setLastMessageTime(now);
        // 重置超时通知标记：用户再次发消息后，新一轮倒计时仍会重新触发 3 分钟提前提醒
        active.setNotifiedBeforeEnd(0);
        serviceRecordMapper.updateById(active);
        return active;
    }

    @Override
    @Transactional
    public void updateFinalStaff(Long conversationId, Long staffId, String staffName) {
        if (staffId == null) {
            return;
        }
        ServiceRecord active = findActive(conversationId);
        if (active == null) {
            touchActive(conversationId, staffId, staffName);
            return;
        }
        active.setStaffId(staffId);
        active.setStaffName(staffName);
        serviceRecordMapper.updateById(active);
        log.info("IM 服务最终处理人更新：conversationId={}, staffId={}", conversationId, staffId);
    }

    @Override
    public void scanTimeoutServices(LocalDateTime warningThreshold, LocalDateTime endThreshold) {
        List<ServiceRecord> inProgress = serviceRecordMapper.selectList(
                new LambdaQueryWrapper<ServiceRecord>()
                        .eq(ServiceRecord::getStatus, STATUS_IN_PROGRESS));
        if (inProgress.isEmpty()) {
            return;
        }
        List<Long> convIds = inProgress.stream()
                .map(ServiceRecord::getConversationId)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, Conversation> convMap = conversationMapper.selectBatchIds(convIds).stream()
                .collect(Collectors.toMap(Conversation::getId, c -> c));

        LocalDateTime now = LocalDateTime.now();
        for (ServiceRecord r : inProgress) {
            LocalDateTime base = r.getLastMessageTime() != null ? r.getLastMessageTime() : r.getStartedAt();
            if (base == null) {
                continue;
            }
            Conversation conv = convMap.get(r.getConversationId());
            if (conv == null) {
                continue;
            }
            // 阶段1：提前通知（CAS 抢占通知标记，防多节点重复广播）
            if (base.isBefore(warningThreshold) && (r.getNotifiedBeforeEnd() == null || r.getNotifiedBeforeEnd() == 0)) {
                boolean claimed = serviceRecordMapper.update(null, new LambdaUpdateWrapper<ServiceRecord>()
                        .eq(ServiceRecord::getId, r.getId())
                        .eq(ServiceRecord::getStatus, STATUS_IN_PROGRESS)
                        .eq(ServiceRecord::getNotifiedBeforeEnd, 0)
                        .set(ServiceRecord::getNotifiedBeforeEnd, 1)) > 0;
                if (claimed) {
                    insertSystemMessage(conv, "service-timeout-warning",
                            "会话即将在 3 分钟后自动结束，请确认您的咨询已得到解答");
                    log.info("IM 服务即将超时提醒：conversationId={}", conv.getId());
                }
            }
            // 阶段2：超时结束（CAS 抢占状态流转，防多节点重复结束）
            if (base.isBefore(endThreshold)) {
                boolean claimed = serviceRecordMapper.update(null, new LambdaUpdateWrapper<ServiceRecord>()
                        .eq(ServiceRecord::getId, r.getId())
                        .eq(ServiceRecord::getStatus, STATUS_IN_PROGRESS)
                        .set(ServiceRecord::getStatus, STATUS_ENDED)
                        .set(ServiceRecord::getEndedAt, now)
                        .set(ServiceRecord::getEndReason, END_REASON_TIMEOUT)) > 0;
                if (claimed) {
                    // 会话进入待接入状态：置空接待客服，等待用户下次发消息时重新分配
                    conversationMapper.update(null, new LambdaUpdateWrapper<Conversation>()
                            .eq(Conversation::getId, conv.getId())
                            .set(Conversation::getAssigneeId, null)
                            .set(Conversation::getAssigneeName, null));
                    insertSystemMessage(conv, "service-ended", "本次服务已结束");
                    log.info("IM 服务超时结束，会话回到待接入：conversationId={}, recordId={}", conv.getId(), r.getId());
                }
            }
        }
    }

    @Override
    public boolean hasActive(Long conversationId) {
        return serviceRecordMapper.selectCount(new LambdaQueryWrapper<ServiceRecord>()
                .eq(ServiceRecord::getConversationId, conversationId)
                .eq(ServiceRecord::getStatus, STATUS_IN_PROGRESS)) > 0;
    }

    @Override
    public ServiceRecord latestRatable(Long conversationId, Long userId) {
        return serviceRecordMapper.selectOne(new LambdaQueryWrapper<ServiceRecord>()
                .eq(ServiceRecord::getConversationId, conversationId)
                .eq(ServiceRecord::getUserId, userId)
                .eq(ServiceRecord::getStatus, STATUS_ENDED)
                .isNull(ServiceRecord::getRating)
                .isNotNull(ServiceRecord::getStaffId)
                .orderByDesc(ServiceRecord::getId)
                .last("limit 1"));
    }

    @Override
    @Transactional
    public ServiceRecord submitRating(Long conversationId, Long userId, Long shopId,
                                     Integer rating, String tags, String comment) {
        if (rating == null || rating < 1 || rating > 5) {
            throw new IllegalArgumentException("评分必须在 1-5 之间");
        }
        ServiceRecord target = latestRatable(conversationId, userId);
        if (target == null) {
            // 幂等：重复提交时返回最近一条已评价记录
            ServiceRecord rated = serviceRecordMapper.selectOne(new LambdaQueryWrapper<ServiceRecord>()
                    .eq(ServiceRecord::getConversationId, conversationId)
                    .eq(ServiceRecord::getUserId, userId)
                    .eq(ServiceRecord::getStatus, STATUS_RATED)
                    .orderByDesc(ServiceRecord::getId)
                    .last("limit 1"));
            if (rated != null) {
                return rated;
            }
            throw new IllegalArgumentException("当前没有可评价的服务");
        }
        target.setRating(rating);
        target.setTags(tags);
        target.setComment(comment);
        target.setStatus(STATUS_RATED);
        serviceRecordMapper.updateById(target);
        log.info("IM 满意度评价提交：conversationId={}, userId={}, staffId={}, rating={}",
                conversationId, userId, target.getStaffId(), rating);
        return target;
    }

    @Override
    public IPage<ServiceRecord> listByShop(Long shopId, Integer page, Integer pageSize) {
        return page(new Page<>(page, pageSize),
                new LambdaQueryWrapper<ServiceRecord>()
                        .eq(ServiceRecord::getShopId, shopId)
                        .isNotNull(ServiceRecord::getRating)
                        .orderByDesc(ServiceRecord::getCreatedAt));
    }

    @Override
    public SatisfactionService.SatisfactionStats stats(Long shopId) {
        long total = count(new LambdaQueryWrapper<ServiceRecord>()
                .eq(ServiceRecord::getShopId, shopId).isNotNull(ServiceRecord::getRating));
        if (total == 0) {
            return new SatisfactionService.SatisfactionStats(0.0, 0, 0, 0, 0, 0, 0);
        }
        long r5 = countRated(shopId, 5);
        long r4 = countRated(shopId, 4);
        long r3 = countRated(shopId, 3);
        long r2 = countRated(shopId, 2);
        long r1 = countRated(shopId, 1);
        double avg = (double) (5 * r5 + 4 * r4 + 3 * r3 + 2 * r2 + 1 * r1) / total;
        return new SatisfactionService.SatisfactionStats(avg, total, r5, r4, r3, r2, r1);
    }

    private long countRated(Long shopId, int rating) {
        return count(new LambdaQueryWrapper<ServiceRecord>()
                .eq(ServiceRecord::getShopId, shopId)
                .eq(ServiceRecord::getRating, rating));
    }

    private ServiceRecord findActive(Long conversationId) {
        return serviceRecordMapper.selectOne(new LambdaQueryWrapper<ServiceRecord>()
                .eq(ServiceRecord::getConversationId, conversationId)
                .eq(ServiceRecord::getStatus, STATUS_IN_PROGRESS)
                .orderByDesc(ServiceRecord::getId)
                .last("limit 1"));
    }

    /** staff 解析优先级：传入 staffId &gt; 会话当前接待客服 &gt; 空（待分配） */
    private void resolveStaff(ServiceRecord record, Conversation conv, Long staffId, String staffName) {
        if (staffId != null) {
            record.setStaffId(staffId);
            record.setStaffName(staffName);
        } else if (conv.getAssigneeId() != null) {
            record.setStaffId(conv.getAssigneeId());
            record.setStaffName(conv.getAssigneeName());
        }
    }

    /** 系统消息落库 + 广播（与 ImServiceImpl.insertSystemMessage 逻辑一致） */
    private void insertSystemMessage(Conversation conversation, String systemType, String content) {
        ImMessage sysMsg = new ImMessage();
        sysMsg.setConversationId(conversation.getId());
        sysMsg.setSenderId(0L);
        sysMsg.setSenderRole("merchant");
        sysMsg.setShopId(conversation.getShopId());
        sysMsg.setUserId(conversation.getUserId());
        sysMsg.setType("text");
        sysMsg.setContent(content);
        sysMsg.setSenderName("系统");
        sysMsg.setSystemType(systemType);
        sysMsg.setRead(true);
        sysMsg.setCreatedAt(LocalDateTime.now());
        sysMsg = mongoTemplate.save(sysMsg);
        imEventProducer.broadcast(new ImBroadcast("message", conversation.getUserId(), conversation.getShopId(), toView(sysMsg)));
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
