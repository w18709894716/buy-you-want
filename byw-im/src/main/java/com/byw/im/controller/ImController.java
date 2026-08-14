package com.byw.im.controller;

import com.byw.common.core.result.PageResult;
import com.byw.common.core.result.R;
import com.byw.common.security.annotation.RequireLogin;
import com.byw.common.security.context.UserContext;
import com.byw.im.dto.ConversationView;
import com.byw.im.dto.MessageView;
import com.byw.im.dto.StaffBriefDTO;
import com.byw.im.entity.Conversation;
import com.byw.im.service.DispatchService;
import com.byw.im.service.ImService;
import com.byw.im.ws.SessionManager;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 客服 IM REST 接口：会话获取、列表、历史消息、已读、未读角标。
 * 消息实时收发走 WebSocket(/ws/im)，此处仅提供拉取与状态维护。
 * 身份由网关注入头 + AuthInterceptor 重建 UserContext；买家/商家按角色区分数据范围。
 */
@Tag(name = "客服IM", description = "客服会话与消息")
@RestController
@RequestMapping("/im")
@RequiredArgsConstructor
public class ImController {

    private final ImService imService;
    private final SessionManager sessionManager;
    private final DispatchService dispatchService;

    @Operation(summary = "获取或创建会话（买家发起）")
    @RequireLogin
    @PostMapping("/conversation")
    public R<Conversation> conversation(@RequestBody ConversationRequest request) {
        if (request == null || request.getShopId() == null) {
            return R.fail("shopId 不能为空");
        }
        return R.ok(imService.getOrCreateConversation(UserContext.getUserId(), request.getShopId(), request.getEntry()));
    }

    @Operation(summary = "会话列表（买家看自己/商家看本店）")
    @RequireLogin
    @GetMapping("/conversations")
    public R<List<ConversationView>> conversations() {
        boolean merchant = UserContext.isMerchant();
        String role = merchant ? "merchant" : "user";
        return R.ok(imService.listConversations(UserContext.getUserId(), UserContext.getShopId(), role));
    }

    @Operation(summary = "会话历史消息分页（时间倒序）")
    @RequireLogin
    @GetMapping("/messages")
    public R<PageResult<MessageView>> messages(
            @RequestParam Long conversationId,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        return R.ok(imService.listMessages(conversationId, page, pageSize));
    }

    @Operation(summary = "标记会话已读")
    @RequireLogin
    @PostMapping("/read")
    public R<Void> read(@RequestBody ReadRequest request) {
        if (request == null || request.getConversationId() == null) {
            return R.fail("conversationId 不能为空");
        }
        String role = UserContext.isMerchant() ? "merchant" : "user";
        // 传操作者ID：商家侧仅接待者/介入者产生已读回执（后端校验）
        imService.markRead(request.getConversationId(), role, UserContext.getUserId());
        return R.ok();
    }

    @Operation(summary = "未读总数（角标）")
    @RequireLogin
    @GetMapping("/unread-total")
    public R<Long> unreadTotal() {
        boolean merchant = UserContext.isMerchant();
        String role = merchant ? "merchant" : "user";
        return R.ok(imService.unreadTotal(UserContext.getUserId(), UserContext.getShopId(), role));
    }

    @Operation(summary = "本店在线可接待客服列表（转接选人）")
    @RequireLogin
    @GetMapping("/staff/online")
    public R<List<StaffBriefDTO>> staffOnline() {
        if (UserContext.getShopId() == null) {
            return R.fail("仅商家端可用");
        }
        return R.ok(imService.listOnlineStaff(UserContext.getShopId()));
    }

    @Operation(summary = "挂起当前客服（不再接新消息，存量会话可继续回复）")
    @RequireLogin
    @PostMapping("/staff/suspend")
    public R<Void> staffSuspend() {
        Long shopId = UserContext.getShopId();
        if (shopId == null) {
            return R.fail("仅商家端可用");
        }
        sessionManager.setSuspended(UserContext.getUserId(), true);
        return R.ok();
    }

    @Operation(summary = "恢复当前客服（取消挂起，恢复接单并触发队列/离线池消费）")
    @RequireLogin
    @PostMapping("/staff/resume")
    public R<Void> staffResume() {
        Long shopId = UserContext.getShopId();
        if (shopId == null) {
            return R.fail("仅商家端可用");
        }
        sessionManager.setSuspended(UserContext.getUserId(), false);
        // 恢复接单后立即消费排队/离线池会话
        dispatchService.consumeQueue(shopId);
        dispatchService.consumeOfflinePool(shopId);
        return R.ok();
    }

    @Operation(summary = "当前客服挂起状态")
    @RequireLogin
    @GetMapping("/staff/state")
    public R<Map<String, Object>> staffState() {
        if (UserContext.getShopId() == null) {
            return R.fail("仅商家端可用");
        }
        return R.ok(Collections.singletonMap("suspended", sessionManager.isStaffSuspended(UserContext.getUserId())));
    }

    @Data
    public static class ConversationRequest {
        private Long shopId;
        /** 入口意图（product-商品详情 order-订单页 shop-店铺首页；可空） */
        private String entry;
    }

    @Data
    public static class ReadRequest {
        private Long conversationId;
    }
}
