package com.byw.common.core.trace;

import org.slf4j.MDC;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 链路追踪ID：请求入口生成或透传，写入 MDC 供日志打印，并经 Feign 向下游透传。
 *
 * <p>项目未接入完整的分布式追踪后端（OTel/Zipkin），这里先用"traceId + MDC + 日志格式 +
 * Feign 透传"这一最小可用组合，让一次跨服务调用的日志可以用同一个 traceId 串起来；
 * 后续接入 Micrometer Tracing 时，只需把本类替换为 Brave/OTel 的上下文即可。
 */
public final class TraceIdHolder {

    /** HTTP 请求/响应头与 Feign 透传使用的头名 */
    public static final String HEADER = "X-Trace-Id";

    /** MDC key，与各服务 logging.pattern.console 中的 %X{traceId} 对应 */
    public static final String MDC_KEY = "traceId";

    /** 允许的最大长度：防止外部塞入超长头污染日志 */
    private static final int MAX_LENGTH = 64;

    public static String get() {
        return MDC.get(MDC_KEY);
    }

    public static void set(String traceId) {
        if (traceId != null && !traceId.isBlank()) {
            MDC.put(MDC_KEY, traceId);
        }
    }

    public static void clear() {
        MDC.remove(MDC_KEY);
    }

    /** 生成一个新的 traceId：时间戳 + 随机数，足够在日志中区分请求 */
    public static String generate() {
        return Long.toHexString(System.currentTimeMillis())
                + Integer.toHexString(ThreadLocalRandom.current().nextInt());
    }

    /**
     * 校验外部传入的 traceId 是否可用：只允许字母/数字/中划线/下划线，
     * 避免把换行等字符写进日志或响应头（日志注入 / 头注入）。
     */
    public static boolean isValid(String value) {
        if (value == null || value.isBlank() || value.length() > MAX_LENGTH) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            boolean allowed = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9') || c == '-' || c == '_';
            if (!allowed) {
                return false;
            }
        }
        return true;
    }

    private TraceIdHolder() {
    }
}
