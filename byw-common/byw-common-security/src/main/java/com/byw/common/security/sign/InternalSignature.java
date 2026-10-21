package com.byw.common.security.sign;

import org.slf4j.Logger;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * 网关与服务之间的身份头签名。
 *
 * <p>鉴权完全依赖 {@code X-User-*} 头，而剥离客户端伪造头、注入真实身份的逻辑只在网关执行；
 * 服务端口一旦可达，直连者自造 {@code X-User-Id: 1, X-User-Role: platform_admin} 即可越权。
 * 网关对这些头做 HMAC-SHA256 签名，服务端只认签名校验通过的身份头，直连伪造的请求退化为游客
 * （受保护接口随后 401），签名不通过也不会误伤公开接口的游客访问。
 *
 * <p>签名只覆盖身份值本身、不带时间戳：身份头无过期时间，重放签名等价于重放原始 token，
 * 不额外扩大风险，同时避免引入服务间时钟偏差这一故障面。
 */
public final class InternalSignature {

    /** 配置项名，可用环境变量 BYW_SECURITY_INTERNAL_SECRET 覆盖 */
    public static final String PROPERTY_NAME = "byw.security.internal-secret";

    /** 未配置时使用的开发默认密钥，仅保证本地开箱即用，生产必须覆盖 */
    public static final String DEFAULT_SECRET = "byw-internal-gateway-secret";

    private static final String ALGORITHM = "HmacSHA256";

    /**
     * 服务间调用凭证的签名主体。与用户身份无关，只证明调用方持有共享密钥，
     * 用于把"内部 Feign 接口"从"对所有人开放的 @Public 接口"里区分出来。
     */
    private static final String INTERNAL_SUBJECT = "byw-internal-caller-v1";

    public static String sign(String secret, String userId, String username, String role, String shopId, String userType) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM));
            byte[] digest = mac.doFinal(canonical(userId, username, role, shopId, userType)
                    .getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("身份头签名计算失败", e);
        }
    }

    public static boolean verify(String secret, String signature, String userId, String username,
                                 String role, String shopId, String userType) {
        if (signature == null || signature.isEmpty()) {
            return false;
        }
        byte[] expected = sign(secret, userId, username, role, shopId, userType).getBytes(StandardCharsets.US_ASCII);
        byte[] actual = signature.getBytes(StandardCharsets.US_ASCII);
        return MessageDigest.isEqual(expected, actual);
    }

    /**
     * 生成服务间调用凭证（固定值，仅依赖共享密钥）。重放危害等价于重放用户身份签名：
     * 拿到它必须先拿到密钥，而密钥泄露时身份签名同样失守，故不额外引入时间戳故障面。
     */
    public static String signInternal(String secret) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM));
            byte[] digest = mac.doFinal(INTERNAL_SUBJECT.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("服务间凭证签名计算失败", e);
        }
    }

    /** 校验服务间调用凭证 */
    public static boolean verifyInternal(String secret, String signature) {
        if (signature == null || signature.isEmpty()) {
            return false;
        }
        byte[] expected = signInternal(secret).getBytes(StandardCharsets.US_ASCII);
        byte[] actual = signature.getBytes(StandardCharsets.US_ASCII);
        return MessageDigest.isEqual(expected, actual);
    }

    /**
     * 用户名写头前的百分号编码。
     *
     * <p>HTTP 头值按 ISO-8859-1 编解码，中文用户名过一趟网络会变成乱码：签名方按原文算 HMAC，
     * 校验方按乱码重算，摘要必然不等，身份头被整体判为伪造 —— C 端表现为"登录成功立刻退出"。
     * 编码后头值是纯 ASCII，可无损传输；<b>签名始终按原文计算</b>，编解码只作用于传输层。
     */
    public static String encodeUsername(String username) {
        return username == null ? null : URLEncoder.encode(username, StandardCharsets.UTF_8);
    }

    /** 还原 {@link #encodeUsername} 的结果；头值非法（如直连者塞入残缺的 % 序列）时返回 null，让验签自然失败而非抛 500 */
    public static String decodeUsername(String encoded) {
        if (encoded == null) {
            return null;
        }
        try {
            return URLDecoder.decode(encoded, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static void warnIfDefaultSecret(String secret, Logger log) {
        if (DEFAULT_SECRET.equals(secret)) {
            log.warn("未配置 {}，正在使用内置开发默认密钥，直连服务端口伪造身份头的风险未消除；"
                    + "生产环境请通过环境变量 BYW_SECURITY_INTERNAL_SECRET 覆盖为随机密钥", PROPERTY_NAME);
        }
    }

    private static String canonical(String userId, String username, String role, String shopId, String userType) {
        return String.join("\n", nullToEmpty(userId), nullToEmpty(username), nullToEmpty(role),
                nullToEmpty(shopId), nullToEmpty(userType));
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private InternalSignature() {}
}
