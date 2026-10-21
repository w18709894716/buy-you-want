package com.byw.pay.support;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;

/**
 * 支付回调验签（真实渠道回调的准入门槛）。
 *
 * <p><b>为什么必须有</b>：{@code POST /pay/callback/{channel}} 是 {@code @Public} 的，
 * 而两个模拟渠道策略的 {@code queryStatus()} 都恒返回"已支付"。也就是说只要没有验签，
 * 任何人都能拿一个 payNo 调一次回调，把订单变成已支付（白拿商品）。
 * 早期实现只靠 {@code mock-enabled=true} 时的"归属用户"校验兜底，一旦按生产要求关掉模拟开关，
 * 这个公开接口就变成完全敞开 —— 属于典型的 fail-open。
 *
 * <p>签名口径：{@code hex(HMAC-SHA256(secret, payNo + "|" + tradeNo + "|" + timestamp))}，
 * 时间戳为毫秒，允许 {@code byw.pay.callback-tolerance-ms} 的时钟偏差（默认 5 分钟）以防重放。
 * 密钥未配置时<b>一律拒绝</b>（fail-closed），而不是放行。
 */
@Slf4j
@Component
public class PayCallbackSignature {

    private static final String ALGORITHM = "HmacSHA256";

    private final String secret;
    private final long toleranceMs;

    public PayCallbackSignature(@Value("${byw.pay.callback-secret:}") String secret,
                               @Value("${byw.pay.callback-tolerance-ms:300000}") long toleranceMs) {
        this.secret = secret;
        this.toleranceMs = toleranceMs;
        if (secret == null || secret.isBlank()) {
            log.warn("byw.pay.callback-secret 未配置：真实渠道支付回调将一律被拒绝（模拟回调不受影响）");
        }
    }

    /**
     * 校验渠道回调签名。
     *
     * @param timestamp 毫秒时间戳（请求头 X-Pay-Timestamp）
     * @param signature 十六进制签名（请求头 X-Pay-Signature）
     */
    public boolean verify(String payNo, String tradeNo, String timestamp, String signature) {
        if (secret == null || secret.isBlank() || timestamp == null || timestamp.isBlank()
                || signature == null || signature.isBlank()) {
            return false;
        }
        long ts;
        try {
            ts = Long.parseLong(timestamp.trim());
        } catch (NumberFormatException e) {
            return false;
        }
        if (Math.abs(System.currentTimeMillis() - ts) > toleranceMs) {
            log.warn("支付回调时间戳超出允许偏差: payNo={}, timestamp={}, toleranceMs={}", payNo, ts, toleranceMs);
            return false;
        }
        byte[] expected = sign(payNo, tradeNo, ts).getBytes(StandardCharsets.UTF_8);
        byte[] actual = signature.trim().toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8);
        // 常数时间比较，避免通过响应时间猜测签名
        return MessageDigest.isEqual(expected, actual);
    }

    /** 生成签名（供渠道对接方/联调脚本使用） */
    public String sign(String payNo, String tradeNo, long timestamp) {
        return hmacHex(payNo + "|" + tradeNo + "|" + timestamp);
    }

    private String hmacHex(String content) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM));
            return HexFormat.of().formatHex(mac.doFinal(content.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            // 算法固定存在，走到这里说明环境异常；抛出以免"验签失败"被误判成"签名不匹配"
            throw new IllegalStateException("支付回调签名计算失败", e);
        }
    }
}
