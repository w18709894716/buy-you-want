package com.byw.im.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.byw.api.order.OrderFeignClient;
import com.byw.api.order.dto.OrderDetailDTO;
import com.byw.api.shop.ShopFeignClient;
import com.byw.api.shop.dto.MerchantAccountDTO;
import com.byw.api.shop.dto.ShopDTO;
import com.byw.api.user.UserFeignClient;
import com.byw.api.user.dto.UserDTO;
import com.byw.common.core.result.PageResult;
import com.byw.common.core.result.R;
import com.byw.im.dto.ConversationView;
import com.byw.im.dto.DispatchResolveResult;
import com.byw.im.dto.ImBroadcast;
import com.byw.im.dto.MessageView;
import com.byw.im.dto.SendMessageCommand;
import com.byw.im.dto.StaffBriefDTO;
import com.byw.im.document.ImMessage;
import com.byw.im.entity.Conversation;
import com.byw.im.entity.ServiceRecord;
import com.byw.im.mapper.ConversationMapper;
import com.byw.im.mapper.ServiceRecordMapper;
import com.byw.im.producer.ImEventProducer;
import com.byw.im.service.ImService;
import com.byw.im.service.DispatchService;
import com.byw.im.service.FaqService;
import com.byw.im.service.ServiceRecordService;
import com.byw.im.ws.SessionManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * 客服 IM 服务实现：会话关系存 MySQL(t_conversation)，消息流存 MongoDB(im_messages)，
 * 收发后经 RocketMQ 广播下推。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImServiceImpl implements ImService {

    private static final String ROLE_USER = "user";
    private static final String ROLE_MERCHANT = "merchant";

    /** 限时撤回窗口：2 分钟（秒） */
    private static final long RECALL_WINDOW_SECONDS = 120L;

    private final ConversationMapper conversationMapper;
    private final MongoTemplate mongoTemplate;
    private final ImEventProducer imEventProducer;
    private final ShopFeignClient shopFeignClient;
    private final UserFeignClient userFeignClient;
    private final SessionManager sessionManager;
    private final DispatchService dispatchService;
    private final OrderFeignClient orderFeignClient;
    private final FaqService faqService;
    private final ServiceRecordService serviceRecordService;
    private final ServiceRecordMapper serviceRecordMapper;

    @Override
    public Conversation getOrCreateConversation(Long userId, Long shopId) {
        Conversation existing = findConversation(userId, shopId);
        if (existing != null) {
            return existing;
        }
        Conversation c = new Conversation();
        c.setUserId(userId);
        c.setShopId(shopId);
        c.setUserUnread(0);
        c.setShopUnread(0);
        try {
            conversationMapper.insert(c);
            return c;
        } catch (DuplicateKeyException e) {
            // 并发下唯一键冲突：重查已存在会话
            return findConversation(userId, shopId);
        }
    }

    private Conversation findConversation(Long userId, Long shopId) {
        return conversationMapper.selectOne(new LambdaQueryWrapper<Conversation>()
                .eq(Conversation::getUserId, userId)
                .eq(Conversation::getShopId, shopId)
                .last("limit 1"));
    }

    @Override
    @Transactional
    public MessageView sendMessage(SendMessageCommand command) {
        boolean fromUser = ROLE_USER.equals(command.getSenderRole());
        Conversation conversation;
        if (command.getConversationId() != null) {
            conversation = conversationMapper.selectById(command.getConversationId());
            if (conversation == null) {
                throw new IllegalArgumentException("会话不存在: " + command.getConversationId());
            }
        } else if (fromUser) {
            conversation = getOrCreateConversation(command.getSenderId(), command.getShopId());
        } else {
            throw new IllegalArgumentException("商家发送消息必须指定 conversationId");
        }

        // 租户/归属校验：商家仅能在本店会话发言；买家仅能在本人会话发言
        if (!fromUser && command.getShopId() != null && !command.getShopId().equals(conversation.getShopId())) {
            throw new IllegalArgumentException("无权在非本店会话发送消息");
        }
        if (fromUser && !command.getSenderId().equals(conversation.getUserId())) {
            throw new IllegalArgumentException("无权在非本人会话发送消息");
        }

        // 只读模式（后端强制）：已分配会话仅接待者/介入者可回复；待接入会话商家回复自动成为接待者
        if (!fromUser) {
            Long current = conversation.getAssigneeId();
            boolean isAssignee = current != null && current.equals(command.getSenderId());
            boolean isJoiner = parseJoiners(conversation.getJoiners()).contains(command.getSenderId());
            if (current == null) {
                takeOverConversation(conversation.getId(), command.getSenderId(),
                        command.getSenderName(), command.getShopId());
            } else if (!isAssignee && !isJoiner) {
                throw new IllegalArgumentException("当前由 " + conversation.getAssigneeName() + " 接待，请先介入或接管");
            }
        }

        // 用户消息 senderName 用昵称填充（聊天框展示用户昵称，替代"用户+ID"）
        if (fromUser) {
            String nickname = resolveUserNickname(command.getSenderId());
            if (nickname != null) {
                command.setSenderName(nickname);
            }
        }
        // 商家消息 senderName 用真实姓名填充（聊天框展示客服名字，替代登录名）
        if (!fromUser && command.getSenderName() != null) {
            command.setSenderName(resolveAssigneeName(command.getSenderId(), command.getSenderName()));
        }

        LocalDateTime now = LocalDateTime.now();
        ImMessage doc = new ImMessage();
        doc.setConversationId(conversation.getId());
        doc.setSenderId(command.getSenderId());
        doc.setSenderRole(command.getSenderRole());
        doc.setShopId(conversation.getShopId());
        doc.setUserId(conversation.getUserId());
        doc.setType(command.getType() == null ? "text" : command.getType());
        doc.setContent(command.getContent());
        doc.setExtra(command.getExtra());
        doc.setSenderName(command.getSenderName());
        doc.setRead(false);
        doc.setCreatedAt(now);
        // 引用消息：校验被引用消息属于同一会话并快照内容（原消息撤回后引用仍可显示）
        if (command.getQuoteId() != null && !command.getQuoteId().isBlank()) {
            ImMessage quoted = mongoTemplate.findById(command.getQuoteId(), ImMessage.class);
            if (quoted != null && conversation.getId().equals(quoted.getConversationId())) {
                doc.setQuoteId(quoted.getId());
                doc.setQuoteContent(summarize(quoted.getType(), quoted.getContent()));
                doc.setQuoteSenderName(quoted.getSenderName());
            }
        }
        doc = mongoTemplate.save(doc);

        // 更新会话摘要与未读
        conversation.setLastMessage(summarize(doc.getType(), doc.getContent()));
        conversation.setLastMessageType(doc.getType());
        conversation.setLastMessageTime(now);
        if (fromUser) {
            conversation.setShopUnread(nz(conversation.getShopUnread()) + 1);
        } else {
            conversation.setUserUnread(nz(conversation.getUserUnread()) + 1);
        }
        conversationMapper.updateById(conversation);

        // 服务记录：无进行中服务则创建（服务开始），有则刷新最后消息时间（重置超时计时）
        // 注意：FAQ 引导点击消息不触发服务（机器人问答阶段，服务始于用户真正找人工时）
        // FAQ 引导点击消息：无论是否有人工接待，一律由机器人即时回复（已接待时回复同样落库广播，客服可见可补充）
        boolean faqClick = fromUser && "text".equals(command.getType())
                && command.getExtra() != null && Boolean.TRUE.equals(command.getExtra().get("faqClick"));
        if (faqClick) {
            String answer = faqService.matchFaq(conversation.getShopId(), command.getContent());
            if (answer != null) {
                // 创建机器人回复消息
                ImMessage robotMsg = new ImMessage();
                robotMsg.setConversationId(conversation.getId());
                robotMsg.setSenderId(0L);
                robotMsg.setSenderRole("robot");
                robotMsg.setShopId(conversation.getShopId());
                robotMsg.setUserId(conversation.getUserId());
                robotMsg.setType("text");
                robotMsg.setContent(answer);
                robotMsg.setSenderName("智能客服");
                robotMsg.setRead(false);
                robotMsg.setCreatedAt(LocalDateTime.now());
                robotMsg = mongoTemplate.save(robotMsg);

                // 更新会话摘要为机器人回复
                conversation.setLastMessage(answer);
                conversation.setLastMessageType("text");
                conversation.setLastMessageTime(robotMsg.getCreatedAt());
                conversationMapper.updateById(conversation);

                // 人工接待中：FAQ 点击也属于活跃互动，刷新服务超时计时（未接待时机器人问答不计入服务，避免无主服务误结束）
                if (conversation.getAssigneeId() != null) {
                    serviceRecordService.touchActive(conversation.getId(), null, null);
                }

                // 广播用户消息
                MessageView userView = toView(doc);
                imEventProducer.broadcast(new ImBroadcast("message", conversation.getUserId(), conversation.getShopId(), userView));

                // 广播机器人回复
                MessageView robotView = toView(robotMsg);
                imEventProducer.broadcast(new ImBroadcast("message", conversation.getUserId(), conversation.getShopId(), robotView));

                // 机器人已回复 = 商家侧已响应：用户提问视为已读（向买家端推已读回执）
                markUserMessagesReadByRobot(conversation);

                log.info("FAQ 机器人已回复：conversationId={}, answer={}", conversation.getId(), answer);
                return robotView;
            }
            // FAQ 点击未命中：机器人问答不进人工分流（不创建服务/不排队/不进离线池）——
            // 避免仅点引导问题的会话进离线池后在客服上线时被误分配；
            // 机器人兜底回复引导用户输入问题（输入的问题才走正常分流）。
            // 人工接待中同步保活服务计时，未命中问题客服可见可补充
            if (conversation.getAssigneeId() != null) {
                serviceRecordService.touchActive(conversation.getId(), null, null);
            }
            MessageView userView = toView(doc);
            imEventProducer.broadcast(new ImBroadcast("message", conversation.getUserId(), conversation.getShopId(), userView));
            robotReply(conversation, "抱歉，这个问题我还不会，请直接输入您的问题，为您转接人工解答");
            log.info("IM FAQ 点击未命中，机器人兜底回复：conversationId={}", conversation.getId());
            return userView;
        }

        // 用户消息且会话无接待客服 → 分流决策（非服务时间/机器人优先/规则匹配/排队/离线池）；
        // 其余（已有接待客服/商家消息）仅保活服务（FAQ 点击消息已提前返回）
        if (fromUser && conversation.getAssigneeId() == null) {
            dispatchPendingConversation(conversation, command, doc);
        } else {
            serviceRecordService.touchActive(conversation.getId(), null, null);
        }

        MessageView view = toView(doc);
        imEventProducer.broadcast(new ImBroadcast("message", conversation.getUserId(), conversation.getShopId(), view));
        return view;
    }

    /**
     * 分流决策树（用户发消息且会话无接待客服时）：
     * <ol>
     *   <li>非服务时间模式：FAQ 命中回复答案、未命中回复提示语；不创建服务、不分配、不进队列不进池</li>
     *   <li>服务时间内 robot_first=true：用户文本消息先走 FAQ 匹配，命中即机器人回复（不创建服务）</li>
     *   <li>人工分流：回头客直分 / 命中规则进绑定分组 / 基础分流；失败 → 排队队列或离线消息池</li>
     * </ol>
     */
    private void dispatchPendingConversation(Conversation conversation, SendMessageCommand command, ImMessage doc) {
        Long shopId = conversation.getShopId();
        // 判定入口意图（沿用卡片类型语义）+ 订单真实状态（Feign 反查，保留）
        String intent = switch (conversation.getLastMessageType()) {
            case "product_card" -> "product";
            case "order_card" -> "order";
            default -> "default";
        };
        Integer orderStatus = resolveOrderStatus(conversation, command);
        DispatchResolveResult resolve = dispatchService.resolveDispatchRule(intent, orderStatus,
                conversation.getUserId(), shopId);

        // 非服务时间模式：机器人默认打开（忽略机器人开关）
        if (!resolve.isInServiceTime()) {
            String reply = faqService.matchFaq(shopId, doc.getContent());
            boolean faqHit = reply != null && !reply.isBlank();
            if (!faqHit) {
                reply = resolve.getOffHoursTip();
            }
            if (reply != null && !reply.isBlank()) {
                robotReply(conversation, reply);
            }
            log.info("IM 非服务时间消息：conversationId={}, shopId={}, 命中FAQ={}, 回复={}",
                    conversation.getId(), shopId, faqHit, reply);
            return;
        }

        // 服务时间内 robot_first：用户文本消息先走 FAQ 匹配，命中即机器人回复（不创建服务）
        if (resolve.isRobotFirst() && "text".equals(doc.getType())) {
            String answer = faqService.matchFaq(shopId, doc.getContent());
            if (answer != null) {
                robotReply(conversation, answer);
                log.info("IM 机器人优先命中 FAQ：conversationId={}, shopId={}", conversation.getId(), shopId);
                return;
            }
        }

        // 进入人工服务：命中规则 → 更新会话所属分组（未命中基础分流保留原分组）
        // 注意：此处不创建服务记录——只有真正分配成功（有客服接待）才由 assign() 内部创建；
        // 排队/离线池阶段没有客服接待，不算服务开始，不应启动超时倒计时
        if (resolve.getGroupId() != null && !resolve.getGroupId().equals(conversation.getDispatchGroupId())) {
            conversation.setDispatchGroupId(resolve.getGroupId());
            conversationMapper.updateById(conversation);
        }
        // 分流落地：回头客直分 / 组内权重 / 基础分流；失败 → 排队或离线池（用户发消息路径，无客服可用时提示用户）
        dispatchService.assignOrQueue(conversation, resolve.getGroupId(), resolve.getRepeatStaffId(), true);
    }

    /**
     * 机器人回复：落库 + 广播（不创建服务、不分配；非服务时间提示语与 robot_first FAQ 回复共用）
     */
    private void robotReply(Conversation conversation, String content) {
        ImMessage robotMsg = new ImMessage();
        robotMsg.setConversationId(conversation.getId());
        robotMsg.setSenderId(0L);
        robotMsg.setSenderRole("robot");
        robotMsg.setShopId(conversation.getShopId());
        robotMsg.setUserId(conversation.getUserId());
        robotMsg.setType("text");
        robotMsg.setContent(content);
        robotMsg.setSenderName("智能客服");
        robotMsg.setRead(false);
        robotMsg.setCreatedAt(LocalDateTime.now());
        robotMsg = mongoTemplate.save(robotMsg);
        // 更新会话摘要为机器人回复
        conversation.setLastMessage(content);
        conversation.setLastMessageType("text");
        conversation.setLastMessageTime(robotMsg.getCreatedAt());
        conversationMapper.updateById(conversation);
        MessageView robotView = toView(robotMsg);
        imEventProducer.broadcast(new ImBroadcast("message", conversation.getUserId(), conversation.getShopId(), robotView));
        // 机器人已回复 = 商家侧已响应：用户提问视为已读（向买家端推已读回执）
        markUserMessagesReadByRobot(conversation);
    }

    /**
     * 机器人回复视为商家侧已自动响应：将会话内用户未读消息全部标记已读并向买家端推已读回执。
     * 不清店铺未读角标：机器人回复不代表人工已处理，角标保留提醒客服跟进；
     * 广播携带 reader=robot 标记，商家端工作台据此跳过角标清零。
     */
    private void markUserMessagesReadByRobot(Conversation conversation) {
        mongoTemplate.updateMulti(
                new Query(Criteria.where("conversationId").is(conversation.getId())
                        .and("senderRole").is(ROLE_USER)
                        .and("read").is(false)),
                new Update().set("read", true),
                ImMessage.class);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("conversationId", conversation.getId());
        data.put("readerRole", ROLE_MERCHANT);
        data.put("receipt", true);
        data.put("reader", "robot");
        imEventProducer.broadcast(new ImBroadcast("read", conversation.getUserId(), conversation.getShopId(), data));
    }

    /**
     * 限时撤回消息：仅发送者本人可撤回，且须在发送后 2 分钟内。
     * 软撤回：content 替换为"消息已撤回"并标记 recalled，保留记录供双方展示。
     */
    @Override
    public void recallMessage(Long conversationId, String messageId, Long operatorId) {
        ImMessage msg = mongoTemplate.findById(messageId, ImMessage.class);
        if (msg == null || !conversationId.equals(msg.getConversationId())) {
            throw new IllegalArgumentException("消息不存在");
        }
        if (msg.getSystemType() != null) {
            throw new IllegalArgumentException("系统消息不可撤回");
        }
        if (!operatorId.equals(msg.getSenderId())) {
            throw new IllegalArgumentException("仅发送者可撤回消息");
        }
        if (Boolean.TRUE.equals(msg.getRecalled())) {
            throw new IllegalArgumentException("消息已撤回");
        }
        if (msg.getCreatedAt() == null
                || Duration.between(msg.getCreatedAt(), LocalDateTime.now()).toSeconds() > RECALL_WINDOW_SECONDS) {
            throw new IllegalArgumentException("超过 2 分钟，无法撤回");
        }
        msg.setRecalled(true);
        msg.setContent("消息已撤回");
        msg = mongoTemplate.save(msg);
        log.info("IM 消息撤回：conversationId={}, messageId={}, senderId={}",
                conversationId, messageId, operatorId);
        MessageView view = toView(msg);
        imEventProducer.broadcast(new ImBroadcast("recall", msg.getUserId(), msg.getShopId(), view));
    }

    @Override
    public List<ConversationView> listConversations(Long userId, Long shopId, String role) {
        boolean merchant = ROLE_MERCHANT.equals(role);
        LambdaQueryWrapper<Conversation> qw = new LambdaQueryWrapper<>();
        if (merchant) {
            qw.eq(Conversation::getShopId, shopId);
        } else {
            qw.eq(Conversation::getUserId, userId);
        }
        qw.orderByDesc(Conversation::getLastMessageTime);
        List<Conversation> list = conversationMapper.selectList(qw);
        // 商家侧：批量查询进行中的服务，标记服务是否活跃（已结束服务的会话不可接入，等用户再次发消息自动分配）
        Set<Long> activeServiceConversationIds = Collections.emptySet();
        if (merchant && !list.isEmpty()) {
            activeServiceConversationIds = serviceRecordMapper.selectList(
                            new LambdaQueryWrapper<ServiceRecord>()
                                    .in(ServiceRecord::getConversationId,
                                            list.stream().map(Conversation::getId).collect(Collectors.toList()))
                                    .eq(ServiceRecord::getStatus, ServiceRecordService.STATUS_IN_PROGRESS))
                    .stream().map(ServiceRecord::getConversationId).collect(Collectors.toSet());
        }
        // 买家侧会话列表需展示店铺真实名称，批量反查（失败降级为空，前端回退"店铺{shopId}"）
        Map<Long, String> shopNameMap = merchant ? Collections.emptyMap()
                : resolveShopNames(list.stream().map(Conversation::getShopId).collect(Collectors.toList()));
        // 商家侧会话列表需展示买家昵称，批量反查（失败降级为空，前端回退"用户{userId}"）
        Map<Long, String> userNicknameMap = merchant
                ? resolveUserNicknames(list.stream().map(Conversation::getUserId).collect(Collectors.toList()))
                : Collections.emptyMap();
        List<ConversationView> result = new ArrayList<>(list.size());
        for (Conversation c : list) {
            ConversationView v = new ConversationView();
            v.setId(c.getId());
            v.setUserId(c.getUserId());
            v.setUserNickname(userNicknameMap.get(c.getUserId()));
            v.setShopId(c.getShopId());
            v.setShopName(shopNameMap.get(c.getShopId()));
            v.setLastMessage(c.getLastMessage());
            v.setLastMessageType(c.getLastMessageType());
            v.setLastMessageTime(c.getLastMessageTime());
            v.setUnread(merchant ? (isMine(c, userId) ? nz(c.getShopUnread()) : 0) : nz(c.getUserUnread()));
            v.setAssigneeId(c.getAssigneeId());
            v.setAssigneeName(c.getAssigneeName());
            v.setJoiners(parseJoiners(c.getJoiners()));
            v.setDispatchGroupId(c.getDispatchGroupId());
            v.setDispatchStatus(c.getDispatchStatus());
            // 服务活跃标记仅商家侧有效（买家侧无接入概念，置 null）
            v.setServiceActive(merchant ? activeServiceConversationIds.contains(c.getId()) : null);
            result.add(v);
        }
        return result;
    }

    /** 批量反查店铺名，失败降级为空 Map。 */
    private Map<Long, String> resolveShopNames(List<Long> shopIds) {
        List<Long> distinct = shopIds.stream().filter(java.util.Objects::nonNull).distinct().collect(Collectors.toList());
        if (distinct.isEmpty()) {
            return Collections.emptyMap();
        }
        try {
            R<List<ShopDTO>> resp = shopFeignClient.getShopsByIds(distinct);
            if (resp != null && resp.isSuccess() && resp.getData() != null) {
                Map<Long, String> map = new HashMap<>();
                for (ShopDTO shop : resp.getData()) {
                    map.put(shop.getId(), shop.getName());
                }
                return map;
            }
        } catch (Exception e) {
            log.warn("批量获取店铺名失败: {}", distinct, e);
        }
        return Collections.emptyMap();
    }

    /** 批量反查买家昵称，失败降级为空 Map。 */
    private Map<Long, String> resolveUserNicknames(List<Long> userIds) {
        List<Long> distinct = userIds.stream().filter(java.util.Objects::nonNull).distinct().collect(Collectors.toList());
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
            log.warn("批量获取买家昵称失败: {}", distinct, e);
        }
        return Collections.emptyMap();
    }

    /** 单查买家昵称（发消息实时填充），失败返回 null。 */
    private String resolveUserNickname(Long userId) {
        try {
            R<UserDTO> resp = userFeignClient.getUserById(userId);
            if (resp != null && resp.isSuccess() && resp.getData() != null) {
                String nick = resp.getData().getNickname();
                if (nick != null && !nick.isBlank()) {
                    return nick;
                }
            }
        } catch (Exception e) {
            log.warn("获取买家昵称失败：userId={}", userId, e);
        }
        return null;
    }

    /** 批量反查客服真实姓名（t_merchant_account.real_name），失败降级为空 Map。 */
    private Map<Long, String> resolveStaffNames(List<Long> staffIds) {
        List<Long> distinct = staffIds.stream().filter(java.util.Objects::nonNull).distinct().collect(Collectors.toList());
        if (distinct.isEmpty()) {
            return Collections.emptyMap();
        }
        try {
            R<List<MerchantAccountDTO>> resp = shopFeignClient.getMerchantsByIds(distinct);
            if (resp != null && resp.isSuccess() && resp.getData() != null) {
                Map<Long, String> map = new HashMap<>();
                for (MerchantAccountDTO acc : resp.getData()) {
                    String name = acc.getRealName();
                    if (name != null && !name.isBlank()) {
                        map.put(acc.getId(), name);
                    }
                }
                return map;
            }
        } catch (Exception e) {
            log.warn("批量获取客服姓名失败: {}", distinct, e);
        }
        return Collections.emptyMap();
    }

    @Override
    public PageResult<MessageView> listMessages(Long conversationId, int page, int size) {
        int pageNum = page < 1 ? 1 : page;
        int pageSize = size < 1 ? 20 : size;
        Criteria criteria = Criteria.where("conversationId").is(conversationId);
        Query countQuery = new Query(criteria);
        long total = mongoTemplate.count(countQuery, ImMessage.class);

        Query query = new Query(criteria)
                // createdAt 相同（同毫秒）的消息排序不稳定会导致分页重复/遗漏，追加 _id 作为次级排序键保证稳定
                .with(Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "_id")))
                .skip((long) (pageNum - 1) * pageSize)
                .limit(pageSize);
        List<ImMessage> docs = mongoTemplate.find(query, ImMessage.class);
        // 视图层修正发送者姓名：用户消息→昵称，客服消息→真实姓名（历史消息 senderName 存的是登录名）
        Map<Long, String> userNicknameMap = resolveUserNicknames(docs.stream()
                .filter(d -> ROLE_USER.equals(d.getSenderRole()))
                .map(ImMessage::getSenderId)
                .filter(java.util.Objects::nonNull).distinct().collect(Collectors.toList()));
        Map<Long, String> staffNameMap = resolveStaffNames(docs.stream()
                .filter(d -> ROLE_MERCHANT.equals(d.getSenderRole()))
                .map(ImMessage::getSenderId)
                .filter(java.util.Objects::nonNull).distinct().collect(Collectors.toList()));
        List<MessageView> views = new ArrayList<>(docs.size());
        for (ImMessage d : docs) {
            MessageView v = toView(d);
            if (ROLE_USER.equals(d.getSenderRole())) {
                String nickname = userNicknameMap.get(d.getSenderId());
                if (nickname != null && !nickname.isBlank()) {
                    v.setSenderName(nickname);
                }
            } else {
                String realName = staffNameMap.get(d.getSenderId());
                if (realName != null && !realName.isBlank()) {
                    v.setSenderName(realName);
                }
            }
            views.add(v);
        }
        return PageResult.of(views, total, pageNum, pageSize);
    }

    @Override
    public void markRead(Long conversationId, String readerRole, Long operatorId) {
        Conversation conversation = conversationMapper.selectById(conversationId);
        if (conversation == null) {
            return;
        }
        boolean readerIsUser = ROLE_USER.equals(readerRole);
        // 商家侧仅接待者/介入者的已读生效：标记消息已读 + 清店铺共享未读 + 广播回执；
        // 其他成员（主账号/非接待客服）旁观打开会话不影响未读，避免清掉接待客服的未读角标
        if (!readerIsUser) {
            Long assignee = conversation.getAssigneeId();
            boolean isAssignee = assignee != null && assignee.equals(operatorId);
            boolean isJoiner = parseJoiners(conversation.getJoiners()).contains(operatorId);
            if (!isAssignee && !isJoiner) {
                return;
            }
        }
        // 读取方读掉对端发来的未读消息
        String peerRole = readerIsUser ? ROLE_MERCHANT : ROLE_USER;
        mongoTemplate.updateMulti(
                new Query(Criteria.where("conversationId").is(conversationId)
                        .and("senderRole").is(peerRole)
                        .and("read").is(false)),
                new Update().set("read", true),
                ImMessage.class);

        if (readerIsUser) {
            conversation.setUserUnread(0);
        } else {
            conversation.setShopUnread(0);
        }
        conversationMapper.updateById(conversation);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("conversationId", conversationId);
        data.put("readerRole", readerRole);
        if (!readerIsUser) {
            data.put("receipt", true);
        }
        imEventProducer.broadcast(new ImBroadcast("read", conversation.getUserId(), conversation.getShopId(), data));
    }

    @Override
    public long unreadTotal(Long userId, Long shopId, String role) {
        boolean merchant = ROLE_MERCHANT.equals(role);
        LambdaQueryWrapper<Conversation> qw = new LambdaQueryWrapper<>();
        if (merchant) {
            qw.eq(Conversation::getShopId, shopId);
        } else {
            qw.eq(Conversation::getUserId, userId);
        }
        long total = 0L;
        for (Conversation c : conversationMapper.selectList(qw)) {
            // 商家角标只统计分配给自己/自己介入的会话（未读是"待我处理"的提醒，他人会话不计入）
            if (merchant && !isMine(c, userId)) {
                continue;
            }
            total += merchant ? nz(c.getShopUnread()) : nz(c.getUserUnread());
        }
        return total;
    }

    /** 会话是否由该客服负责（接待者或介入者） */
    private boolean isMine(Conversation c, Long staffId) {
        return (c.getAssigneeId() != null && c.getAssigneeId().equals(staffId))
                || parseJoiners(c.getJoiners()).contains(staffId);
    }

    @Override
    public void broadcastTyping(Long conversationId, String senderRole) {
        Conversation conversation = conversationMapper.selectById(conversationId);
        if (conversation == null) {
            return;
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("conversationId", conversationId);
        data.put("senderRole", senderRole);
        imEventProducer.broadcast(new ImBroadcast("typing", conversation.getUserId(), conversation.getShopId(), data));
    }

    @Override
    public boolean autoAssignConversation(Long conversationId, Long shopId) {
        return dispatchService.tryAssignConversation(conversationId, shopId, null);
    }

    /**
     * 分流条件之订单真实状态反查：当前消息为订单卡片则直接取 orderNo，
     * 否则取会话内最近一条订单卡片；再经 Feign 反查订单真实状态，
     * 失败降级 null（订单入口将无法命中配置了订单状态条件的规则，落到无状态条件规则或基础分流）。
     */
    private Integer resolveOrderStatus(Conversation conversation, SendMessageCommand command) {
        String orderNo = null;
        if ("order_card".equals(command.getType()) && command.getExtra() != null) {
            Object no = command.getExtra().get("orderNo");
            if (no != null) {
                orderNo = String.valueOf(no);
            }
        }
        if (orderNo == null || orderNo.isBlank()) {
            ImMessage lastCard = mongoTemplate.findOne(
                    Query.query(Criteria.where("conversationId").is(conversation.getId())
                                    .and("type").is("order_card"))
                            .with(Sort.by(Sort.Direction.DESC, "createdAt"))
                            .limit(1),
                    ImMessage.class);
            if (lastCard != null && lastCard.getExtra() != null) {
                Object no = lastCard.getExtra().get("orderNo");
                if (no != null) {
                    orderNo = String.valueOf(no);
                }
            }
        }
        if (orderNo == null || orderNo.isBlank()) {
            return null;
        }
        try {
            R<OrderDetailDTO> resp = orderFeignClient.getOrderDetail(orderNo);
            if (resp != null && resp.isSuccess() && resp.getData() != null) {
                return resp.getData().getStatus();
            }
        } catch (Exception e) {
            log.warn("IM 分流：订单状态反查失败，降级不参与订单状态匹配：orderNo={}", orderNo);
        }
        return null;
    }

    @Override
    @Transactional
    public void takeConversation(Long conversationId, Long staffId, String staffName, Long shopId) {
        Conversation conversation = conversationMapper.selectById(conversationId);
        if (conversation == null) {
            throw new IllegalArgumentException("会话不存在: " + conversationId);
        }
        // 租户校验：仅能接入本店会话
        if (shopId != null && !shopId.equals(conversation.getShopId())) {
            throw new IllegalArgumentException("无权接入非本店会话");
        }
        // 服务状态校验：已结束服务的会话不可接入（无用户消息，接入无意义；等用户再次发消息自动分配）
        if (!serviceRecordService.hasActive(conversationId)) {
            throw new IllegalArgumentException("该会话服务已结束，等待用户再次发起消息后自动分配");
        }
        // 并发保护：仅待接入状态可接入（已被自动分配/其他客服接入则跳过，不抢占）
        if (conversation.getAssigneeId() != null) {
            log.info("IM 客服主动接入跳过（会话已有接待客服）：conversationId={}, assigneeId={}, staffId={}",
                    conversationId, conversation.getAssigneeId(), staffId);
            return;
        }
        String assigneeName = resolveAssigneeName(staffId, staffName);
        conversation.setAssigneeId(staffId);
        conversation.setAssigneeName(assigneeName);
        // 主动接入排队/待接入会话：出队（清空分流状态）
        conversation.setDispatchStatus(null);
        conversation.setDispatchAt(null);
        conversationMapper.updateById(conversation);
        log.info("IM 客服主动接入会话：conversationId={}, shopId={}, assigneeId={}, assigneeName={}",
                conversationId, conversation.getShopId(), staffId, assigneeName);
        insertSystemMessage(conversation, staffId, assigneeName, "assign", "客服 " + assigneeName + " 已接入聊天");
        // 服务记录：接入即服务开始，指定最终处理人（含掉线后重接入场景）
        serviceRecordService.updateFinalStaff(conversation.getId(), staffId, assigneeName);
    }

    @Override
    @Transactional
    public void takeOverConversation(Long conversationId, Long staffId, String staffName, Long shopId) {
        Conversation conversation = conversationMapper.selectById(conversationId);
        if (conversation == null) {
            throw new IllegalArgumentException("会话不存在: " + conversationId);
        }
        // 租户校验：仅能操作本店会话
        if (shopId != null && !shopId.equals(conversation.getShopId())) {
            throw new IllegalArgumentException("无权操作非本店会话");
        }
        // 服务状态校验：已结束服务的会话不可接入/接管（无用户消息，接入无意义；等用户再次发消息自动分配）
        if (!serviceRecordService.hasActive(conversationId)) {
            throw new IllegalArgumentException("该会话服务已结束，等待用户再次发起消息后自动分配");
        }
        Long current = conversation.getAssigneeId();
        // 已是接待者：无需操作
        if (current != null && current.equals(staffId)) {
            log.info("IM 接管跳过（已是接待者）：conversationId={}, staffId={}", conversationId, staffId);
            return;
        }
        String assigneeName = resolveAssigneeName(staffId, staffName);
        boolean wasAssigned = current != null;
        conversation.setAssigneeId(staffId);
        conversation.setAssigneeName(assigneeName);
        // 接管排队/待接入会话：出队（清空分流状态）
        conversation.setDispatchStatus(null);
        conversation.setDispatchAt(null);
        // 接管后把自己从介入者集合移除，避免同时是接待者和介入者的重复身份
        List<Long> joiners = parseJoiners(conversation.getJoiners());
        if (joiners.remove(staffId)) {
            conversation.setJoiners(joiners.isEmpty() ? null : serializeJoiners(joiners));
        }
        conversationMapper.updateById(conversation);
        log.info("IM 客服接管会话：conversationId={}, shopId={}, 原接待者={}, 新接待者={}",
                conversationId, conversation.getShopId(), current, staffId);
        // 待接入 → assign 文案；已分配 → takeover 文案
        String systemType = wasAssigned ? "takeover" : "assign";
        String content = wasAssigned ? "客服 " + assigneeName + " 已接管会话" : "客服 " + assigneeName + " 已接入聊天";
        insertSystemMessage(conversation, staffId, assigneeName, systemType, content);
        // 服务记录：接管后最终处理人变更为当前客服（评价对象随之更新）
        serviceRecordService.updateFinalStaff(conversation.getId(), staffId, assigneeName);
    }

    @Override
    @Transactional
    public void joinConversation(Long conversationId, Long staffId, String staffName, Long shopId) {
        Conversation conversation = conversationMapper.selectById(conversationId);
        if (conversation == null) {
            throw new IllegalArgumentException("会话不存在: " + conversationId);
        }
        // 租户校验：仅能操作本店会话
        if (shopId != null && !shopId.equals(conversation.getShopId())) {
            throw new IllegalArgumentException("无权操作非本店会话");
        }
        // 待接入会话无接待者可介入：直接接管成为接待者
        if (conversation.getAssigneeId() == null) {
            takeOverConversation(conversationId, staffId, staffName, shopId);
            return;
        }
        // 已是接待者：无需介入
        if (conversation.getAssigneeId().equals(staffId)) {
            log.info("IM 介入跳过（已是接待者）：conversationId={}, staffId={}", conversationId, staffId);
            return;
        }
        List<Long> joiners = parseJoiners(conversation.getJoiners());
        if (joiners.contains(staffId)) {
            log.info("IM 介入跳过（已在介入列表）：conversationId={}, staffId={}", conversationId, staffId);
            return;
        }
        joiners.add(staffId);
        conversation.setJoiners(serializeJoiners(joiners));
        conversationMapper.updateById(conversation);
        String joinName = resolveAssigneeName(staffId, staffName);
        log.info("IM 客服介入会话：conversationId={}, shopId={}, 接待者={}, 介入者={}",
                conversationId, conversation.getShopId(), conversation.getAssigneeId(), joiners);
        insertSystemMessage(conversation, staffId, joinName, "join", "客服 " + joinName + " 已介入会话");
    }

    @Override
    @Transactional
    public void transferConversation(Long conversationId, Long operatorId, String operatorName,
                                     Long targetStaffId, Long shopId) {
        Conversation conversation = conversationMapper.selectById(conversationId);
        if (conversation == null) {
            throw new IllegalArgumentException("会话不存在: " + conversationId);
        }
        // 租户校验：仅能操作本店会话
        if (shopId != null && !shopId.equals(conversation.getShopId())) {
            throw new IllegalArgumentException("无权操作非本店会话");
        }
        // 仅当前接待客服可转接
        Long current = conversation.getAssigneeId();
        if (current == null || !current.equals(operatorId)) {
            throw new IllegalArgumentException("仅接待客服可转接会话");
        }
        if (targetStaffId == null || targetStaffId.equals(operatorId)) {
            throw new IllegalArgumentException("转接目标客服无效");
        }
        // 目标须为本店在线且活跃的客服
        if (!sessionManager.isStaffOnline(targetStaffId)
                || !sessionManager.getOnlineStaffIds(shopId).contains(targetStaffId)) {
            throw new IllegalArgumentException("目标客服不在线");
        }
        String targetName = resolveAssigneeName(targetStaffId, String.valueOf(targetStaffId));
        String operatorDisplay = resolveAssigneeName(operatorId, operatorName);
        conversation.setAssigneeId(targetStaffId);
        conversation.setAssigneeName(targetName);
        conversationMapper.updateById(conversation);
        log.info("IM 会话转接：conversationId={}, shopId={}, from={}, to={}",
                conversationId, shopId, operatorId, targetStaffId);
        // senderId 为最新接待者，商家端据此同步会话归属
        insertSystemMessage(conversation, targetStaffId, targetName, "transfer",
                "客服 " + operatorDisplay + " 已将对话转接给客服 " + targetName);
        // 服务记录：转接后最终处理人变更为目标客服（评价对象随之更新）
        serviceRecordService.updateFinalStaff(conversation.getId(), targetStaffId, targetName);
    }

    @Override
    public List<StaffBriefDTO> listOnlineStaff(Long shopId) {
        Set<Long> onlineStaffIds = sessionManager.getOnlineStaffIds(shopId);
        if (onlineStaffIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<StaffBriefDTO> result = new ArrayList<>(onlineStaffIds.size());
        for (Long sid : onlineStaffIds) {
            // 过滤已断开但 Redis 残留在线标记的假在线客服
            if (!sessionManager.isStaffOnline(sid)) {
                continue;
            }
            StaffBriefDTO dto = new StaffBriefDTO();
            dto.setId(sid);
            dto.setName(resolveAssigneeName(sid, String.valueOf(sid)));
            result.add(dto);
        }
        return result;
    }

    /** 解析介入客服ID集合（JSON数组字符串，脏数据兼容返回空） */
    private List<Long> parseJoiners(String joiners) {
        if (joiners == null || joiners.isBlank()) {
            return new ArrayList<>();
        }
        List<Long> result = new ArrayList<>();
        for (String part : joiners.replace("[", "").replace("]", "").split(",")) {
            try {
                result.add(Long.valueOf(part.trim()));
            } catch (NumberFormatException ignored) {
                // 忽略脏数据片段
            }
        }
        return result;
    }

    /** 序列化介入客服ID集合为 JSON 数组字符串，空集合返回 null */
    private String serializeJoiners(Collection<Long> joiners) {
        if (joiners == null || joiners.isEmpty()) {
            return null;
        }
        return joiners.stream().map(String::valueOf).collect(Collectors.joining(",", "[", "]"));
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

    @Override
    public void releaseStaffConversations(Long shopId, Long staffId) {
        // 先查出名下会话：释放后需尝试重新分配（服务未结束，不中断服务）
        List<Conversation> released = conversationMapper.selectList(
                new LambdaQueryWrapper<Conversation>()
                        .eq(Conversation::getShopId, shopId)
                        .eq(Conversation::getAssigneeId, staffId));
        // 注意：updateById 默认 NOT_NULL 策略会忽略 null 字段，必须用 UpdateWrapper.set 显式置空
        int updated = conversationMapper.update(null, new LambdaUpdateWrapper<Conversation>()
                .eq(Conversation::getShopId, shopId)
                .eq(Conversation::getAssigneeId, staffId)
                .set(Conversation::getAssigneeId, null)
                .set(Conversation::getAssigneeName, null));
        log.info("IM 客服下线释放会话：staffId={}, shopId={}, 释放数={}", staffId, shopId, updated);
        // 介入者维度：把下线的介入客服从介入集合移除（会话量可控，全量扫描后内存剔除）
        List<Conversation> withJoiners = conversationMapper.selectList(
                new LambdaQueryWrapper<Conversation>().isNotNull(Conversation::getJoiners));
        int removed = 0;
        for (Conversation c : withJoiners) {
            List<Long> joiners = parseJoiners(c.getJoiners());
            if (joiners.remove(staffId)) {
                c.setJoiners(joiners.isEmpty() ? null : serializeJoiners(joiners));
                conversationMapper.updateById(c);
                removed++;
            }
        }
        if (removed > 0) {
            log.info("IM 客服下线移除介入身份：staffId={}, shopId={}, 会话数={}", staffId, shopId, removed);
        }
        // 掉线重分配：服务未结束的会话立即尝试分配给其他在线客服；失败则进队列/离线池等待消费
        for (Conversation c : released) {
            if (!serviceRecordService.hasActive(c.getId())) {
                continue; // 无进行中服务（如从未开始服务），无需处理
            }
            if (autoAssignConversation(c.getId(), c.getShopId())) {
                continue; // 已重新分配成功，服务由新客服继续
            }
            // 无在线客服可分配：静默进排队队列或离线消息池（用户未发新消息，不主动打扰；等用户再发消息时才提示）
            Conversation after = conversationMapper.selectById(c.getId());
            if (after != null && after.getAssigneeId() == null) {
                dispatchService.assignOrQueue(after, after.getDispatchGroupId(), null, false);
            }
        }
        // 腾出接待能力后：消费本店排队队列/离线消息池
        dispatchService.consumeQueue(shopId);
        dispatchService.consumeOfflinePool(shopId);
    }

    @Override
    public void notifySatisfactionSubmitted(Long conversationId, Integer rating) {
        Conversation conversation = conversationMapper.selectById(conversationId);
        if (conversation == null) {
            log.warn("IM 满意度系统提示跳过：会话不存在 conversationId={}", conversationId);
            return;
        }
        insertSystemMessage(conversation, 0L, "系统", "satisfaction",
                "感谢您的评价，您的反馈将帮助我们不断提升服务质量");
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

    private String summarize(String type, String content) {
        if (type == null) {
            return content;
        }
        return switch (type) {
            case "image" -> "[图片]";
            case "product_card" -> "[商品]";
            case "order_card" -> "[订单]";
            default -> {
                if (content == null) {
                    yield "";
                }
                yield content.length() > 200 ? content.substring(0, 200) : content;
            }
        };
    }

    private int nz(Integer v) {
        return v == null ? 0 : v;
    }
}
