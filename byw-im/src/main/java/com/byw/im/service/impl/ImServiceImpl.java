package com.byw.im.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.byw.api.shop.ShopFeignClient;
import com.byw.api.shop.dto.ShopDTO;
import com.byw.common.core.result.PageResult;
import com.byw.common.core.result.R;
import com.byw.im.dto.ConversationView;
import com.byw.im.dto.ImBroadcast;
import com.byw.im.dto.MessageView;
import com.byw.im.dto.SendMessageCommand;
import com.byw.im.document.ImMessage;
import com.byw.im.entity.Conversation;
import com.byw.im.mapper.ConversationMapper;
import com.byw.im.producer.ImEventProducer;
import com.byw.im.service.ImService;
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

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    private final ConversationMapper conversationMapper;
    private final MongoTemplate mongoTemplate;
    private final ImEventProducer imEventProducer;
    private final ShopFeignClient shopFeignClient;

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
        doc.setRead(false);
        doc.setCreatedAt(now);
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

        MessageView view = toView(doc);
        imEventProducer.broadcast(new ImBroadcast("message", conversation.getUserId(), conversation.getShopId(), view));
        return view;
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
        // 买家侧会话列表需展示店铺真实名称，批量反查（失败降级为空，前端回退"店铺{shopId}"）
        Map<Long, String> shopNameMap = merchant ? Collections.emptyMap()
                : resolveShopNames(list.stream().map(Conversation::getShopId).collect(Collectors.toList()));
        List<ConversationView> result = new ArrayList<>(list.size());
        for (Conversation c : list) {
            ConversationView v = new ConversationView();
            v.setId(c.getId());
            v.setUserId(c.getUserId());
            v.setShopId(c.getShopId());
            v.setShopName(shopNameMap.get(c.getShopId()));
            v.setLastMessage(c.getLastMessage());
            v.setLastMessageType(c.getLastMessageType());
            v.setLastMessageTime(c.getLastMessageTime());
            v.setUnread(merchant ? nz(c.getShopUnread()) : nz(c.getUserUnread()));
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

    @Override
    public PageResult<MessageView> listMessages(Long conversationId, int page, int size) {
        int pageNum = page < 1 ? 1 : page;
        int pageSize = size < 1 ? 20 : size;
        Criteria criteria = Criteria.where("conversationId").is(conversationId);
        Query countQuery = new Query(criteria);
        long total = mongoTemplate.count(countQuery, ImMessage.class);

        Query query = new Query(criteria)
                .with(Sort.by(Sort.Direction.DESC, "createdAt"))
                .skip((long) (pageNum - 1) * pageSize)
                .limit(pageSize);
        List<ImMessage> docs = mongoTemplate.find(query, ImMessage.class);
        List<MessageView> views = new ArrayList<>(docs.size());
        for (ImMessage d : docs) {
            views.add(toView(d));
        }
        return PageResult.of(views, total, pageNum, pageSize);
    }

    @Override
    public void markRead(Long conversationId, String readerRole) {
        Conversation conversation = conversationMapper.selectById(conversationId);
        if (conversation == null) {
            return;
        }
        boolean readerIsUser = ROLE_USER.equals(readerRole);
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
            total += merchant ? nz(c.getShopUnread()) : nz(c.getUserUnread());
        }
        return total;
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
