package com.byw.im.controller;

import com.byw.common.core.result.PageResult;
import com.byw.common.core.result.R;
import com.byw.common.security.annotation.RequireLogin;
import com.byw.common.security.context.UserContext;
import com.byw.im.dto.ConversationView;
import com.byw.im.dto.MessageView;
import com.byw.im.entity.Conversation;
import com.byw.im.service.ImService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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

    @Operation(summary = "获取或创建会话（买家发起）")
    @RequireLogin
    @PostMapping("/conversation")
    public R<Conversation> conversation(@RequestBody ConversationRequest request) {
        if (request == null || request.getShopId() == null) {
            return R.fail("shopId 不能为空");
        }
        return R.ok(imService.getOrCreateConversation(UserContext.getUserId(), request.getShopId()));
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
        imService.markRead(request.getConversationId(), role);
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

    @Data
    public static class ConversationRequest {
        private Long shopId;
    }

    @Data
    public static class ReadRequest {
        private Long conversationId;
    }
}
