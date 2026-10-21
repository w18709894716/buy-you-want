package com.byw.im.config;

import com.byw.im.document.ImMessage;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexOperations;

import java.time.Duration;

/**
 * IM 消息保留策略：给 {@code im_messages} 建 Mongo TTL 索引，超期消息由 Mongo 自动清理。
 *
 * <p><b>为什么需要</b>：{@code im_messages} 是只追加的海量集合，原先没有任何索引与保留策略，
 * 会话越多、时间越长，集合与磁盘只会单向增长（复审 P2-9 指出"消息无保留策略"）。
 *
 * <p><b>为什么用程序建索引而不是 @Indexed 注解</b>：注解方式需要打开
 * {@code spring.data.mongodb.auto-index-creation}（会顺带创建所有注解索引），
 * 且保留天数无法配置；这里显式 {@code ensureIndex}，既幂等又不影响其它索引。
 *
 * <p>关闭方式：{@code byw.im.message-retention-days <= 0}。关闭后超期消息不会被清理。
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class ImMessageRetentionConfig {

    /** TTL 索引名：固定名字，保证重复启动时是"更新同一个索引"而不是不断新增 */
    private static final String TTL_INDEX_NAME = "ttl_created_at";

    private final MongoTemplate mongoTemplate;

    @Value("${byw.im.message-retention-days:180}")
    private long retentionDays;

    @PostConstruct
    public void ensureTtlIndex() {
        if (retentionDays <= 0) {
            log.warn("IM 消息保留策略已关闭（byw.im.message-retention-days={}）：im_messages 不会被自动清理", retentionDays);
            return;
        }
        IndexOperations indexOps = mongoTemplate.indexOps(ImMessage.class);
        indexOps.ensureIndex(new Index()
                .on("createdAt", Sort.Direction.ASC)
                .expire(Duration.ofDays(retentionDays))
                .named(TTL_INDEX_NAME));
        log.info("IM 消息保留策略：保留 {} 天，Mongo TTL 索引 {} 已确保存在（集合 im_messages）",
                retentionDays, TTL_INDEX_NAME);
    }
}
