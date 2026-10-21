package com.byw.settle.util;

import com.byw.common.core.util.SnowflakeIdGenerator;

/**
 * 单号生成器：前缀 + 雪花ID。
 *
 * <p>原实现是"yyyyMMddHHmmss + 6位随机数"，同一秒内并发量上来就会出现重复；
 * 而 t_settle_record/t_withdraw_record/t_balance_flow 的单号列都是唯一索引，
 * 一旦撞号就是一次业务失败。改用雪花ID后全局唯一且趋势递增。
 */
public final class NoGenerator {

    private NoGenerator() {
    }

    public static String generate(String prefix) {
        return prefix + SnowflakeIdGenerator.nextIdStr();
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
