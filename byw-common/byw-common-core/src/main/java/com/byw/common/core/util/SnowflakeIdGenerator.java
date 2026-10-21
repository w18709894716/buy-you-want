package com.byw.common.core.util;

import cn.hutool.core.lang.Snowflake;
import cn.hutool.core.util.IdUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.security.SecureRandom;

/**
 * 雪花ID生成器（全局唯一、趋势递增）。
 *
 * <p><b>workerId 必须多实例唯一</b>，否则两个实例会在同一毫秒生成完全相同的 ID。
 * 原实现固定使用 {@code IdUtil.getSnowflake(1, 1)}，多实例部署下订单号/支付单号存在撞号风险。
 *
 * <p>取值优先级：
 * <ol>
 *   <li>环境变量 {@code BYW_SNOWFLAKE_WORKER_ID}</li>
 *   <li>系统属性 {@code byw.snowflake.worker-id}</li>
 *   <li>主机名哈希（兜底，只能降低撞号概率，不保证唯一）</li>
 * </ol>
 * 生产环境请为每个实例显式配置 0~31 的唯一值。
 */
public final class SnowflakeIdGenerator {

    private static final Logger log = LoggerFactory.getLogger(SnowflakeIdGenerator.class);

    /** Hutool 雪花算法 workerId/datacenterId 的取值范围 */
    private static final int MAX_WORKER_ID = 31;

    private static final Snowflake SNOWFLAKE = createSnowflake();

    public static long nextId() {
        return SNOWFLAKE.nextId();
    }

    public static String nextIdStr() {
        return SNOWFLAKE.nextIdStr();
    }

    private static Snowflake createSnowflake() {
        Long configured = configuredWorkerId();
        if (configured != null) {
            log.info("雪花ID使用显式配置的 workerId={}", configured);
            return IdUtil.getSnowflake(configured, 0);
        }
        long derived = Math.floorMod(hostKey(), MAX_WORKER_ID + 1);
        log.warn("未配置 BYW_SNOWFLAKE_WORKER_ID，雪花ID 按主机名推导 workerId={}；"
                + "多实例部署请为每个实例显式配置唯一值(0-31)，否则仍可能生成重复ID", derived);
        return IdUtil.getSnowflake(derived, 0);
    }

    private static Long configuredWorkerId() {
        String raw = System.getenv("BYW_SNOWFLAKE_WORKER_ID");
        if (raw == null || raw.isBlank()) {
            raw = System.getProperty("byw.snowflake.worker-id");
        }
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            long value = Long.parseLong(raw.trim());
            if (value < 0 || value > MAX_WORKER_ID) {
                log.error("BYW_SNOWFLAKE_WORKER_ID 超出范围(0-31)，已忽略: {}", raw);
                return null;
            }
            return value;
        } catch (NumberFormatException e) {
            log.error("BYW_SNOWFLAKE_WORKER_ID 不是数字，已忽略: {}", raw);
            return null;
        }
    }

    /** 主机名哈希作为兜底 workerId；取不到主机名时用随机数，避免多实例全部落到同一个值 */
    private static int hostKey() {
        try {
            String host = InetAddress.getLocalHost().getHostName();
            if (host != null && !host.isBlank()) {
                return host.hashCode();
            }
        } catch (Exception e) {
            log.warn("获取主机名失败，雪花 workerId 退化为随机值: {}", e.getMessage());
        }
        return new SecureRandom().nextInt();
    }

    private SnowflakeIdGenerator() {
    }
}
