package com.byw.settle.util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 单号生成器：前缀 + yyyyMMddHHmmss + 6位随机数。
 */
public final class NoGenerator {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private NoGenerator() {
    }

    public static String generate(String prefix) {
        return prefix + LocalDateTime.now().format(FMT) + String.format("%06d", ThreadLocalRandom.current().nextInt(1000000));
    }

    /** 结算单号 */
    public static String settleNo() {
        return generate("ST");
    }

    /** 提现单号 */
    public static String withdrawNo() {
        return generate("WD");
    }

    /** 流水号 */
    public static String flowNo() {
        return generate("FL");
    }
}
