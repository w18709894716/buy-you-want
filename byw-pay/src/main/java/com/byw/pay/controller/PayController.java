package com.byw.pay.controller;

import com.byw.api.pay.dto.PayOrderDTO;
import com.byw.common.core.exception.BusinessException;
import com.byw.common.core.exception.ResultCode;
import com.byw.common.core.result.R;
import com.byw.common.security.annotation.Public;
import com.byw.common.security.annotation.RequireAdmin;
import com.byw.common.security.annotation.RequireLogin;
import com.byw.common.security.context.UserContext;
import com.byw.pay.service.PayService;
import com.byw.pay.support.PayCallbackSignature;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@Tag(name = "支付", description = "支付管理")
@Slf4j
@RestController
@RequestMapping("/pay")
@RequiredArgsConstructor
@RequireLogin
public class PayController {

    private final PayService payService;
    private final PayCallbackSignature payCallbackSignature;

    /** 支付金额由服务端按订单 payAmount 计算，入参 amount 仅用于核对，可缺省 */
    @Operation(summary = "创建支付单")
    @PostMapping("/create")
    public R<PayOrderDTO> create(@RequestParam String orderNo,
                                 @RequestParam(required = false) BigDecimal amount,
                                 @RequestParam String channel) {
        Long userId = UserContext.getUserId();
        return R.ok(payService.createPayOrder(orderNo, userId, amount, channel));
    }

    @Operation(summary = "查询支付状态")
    @GetMapping("/status/{payNo}")
    public R<Integer> status(@PathVariable String payNo) {
        Long ownerId = payService.getPayOrderUserId(payNo);
        if (ownerId == null || !ownerId.equals(UserContext.getUserId())) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
        return R.ok(payService.getPayStatus(payNo));
    }

    /**
     * 真实渠道的支付回调（公开接口，靠签名准入）。
     *
     * <p>两个模拟渠道策略的 {@code queryStatus()} 恒返回"已支付"，所以这个公开接口
     * <b>必须</b>先验签再进业务，否则任何人都能把任意 payNo 置为已支付（白拿商品）。
     * 密钥未配置时一律拒绝（fail-closed）。
     */
    @Operation(summary = "支付回调（渠道，需验签）")
    @PostMapping("/callback/{channel}")
    @Public
    public R<Void> callback(@PathVariable String channel,
                            @RequestParam String payNo,
                            @RequestParam String tradeNo,
                            @RequestHeader(value = "X-Pay-Timestamp", required = false) String timestamp,
                            @RequestHeader(value = "X-Pay-Signature", required = false) String signature,
                            @RequestBody(required = false) String callbackContent) {
        if (!payCallbackSignature.verify(payNo, tradeNo, timestamp, signature)) {
            log.warn("支付回调验签失败，拒绝处理: channel={}, payNo={}, timestamp={}", channel, payNo, timestamp);
            throw new BusinessException(ResultCode.UNAUTHORIZED, "支付回调验签失败");
        }
        payService.handlePayCallback(channel, payNo, tradeNo, callbackContent);
        return R.ok();
    }

    /**
     * 模拟支付回调：仅本地/演示使用（{@code byw.pay.mock-enabled=true}），且必须登录且是支付单归属用户。
     * 生产关掉开关后本接口返回 403。
     */
    @Operation(summary = "模拟支付回调（仅本地/演示）")
    @PostMapping("/mock/callback/{channel}")
    public R<Void> mockCallback(@PathVariable String channel,
                                @RequestParam String payNo,
                                @RequestParam String tradeNo) {
        payService.handleMockCallback(channel, payNo, tradeNo);
        return R.ok();
    }

    /** 平台手工退款；refNo 为幂等业务键，重复提交同一 refNo 只退一次 */
    @Operation(summary = "退款")
    @PostMapping("/refund")
    @RequireAdmin
    public R<Void> refund(@RequestParam String orderNo,
                          @RequestParam BigDecimal amount,
                          @RequestParam String reason,
                          @RequestParam(required = false) String refNo) {
        payService.refund(orderNo, amount, reason, refNo);
        return R.ok();
    }
}
