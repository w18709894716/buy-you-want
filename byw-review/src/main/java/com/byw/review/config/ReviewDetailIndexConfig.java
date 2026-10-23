package com.byw.review.config;

import com.byw.review.document.ReviewDetail;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexOperations;

/**
 * 给 {@code review_details} 建查询索引。
 *
 * <p><b>为什么需要</b>：这个集合原先一个索引都没有，而它的读路径全是等值/排序查询 ——
 * 商品评价列表（{@code productId} 等值 + {@code createdAt} 倒序 + skip/limit）、
 * 追评定位（{@code orderId + userId + skuId}）、订单评价回查（{@code orderId + userId}）。
 * 没有索引时每一次都是全集合扫描，评价越多越慢，且 {@code skip} 越翻到后面越贵。
 *
 * <p><b>为什么用程序建索引而不是 @CompoundIndex 注解</b>：注解方式要求打开
 * {@code spring.data.mongodb.auto-index-creation}，那会把这两个集合上所有注解索引一起放开；
 * 这里显式 {@code ensureIndex}，幂等、不影响其它索引，也不需要额外配置项
 * （与 {@code byw-im} 的 {@code ImMessageRetentionConfig} 采用同一做法）。
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class ReviewDetailIndexConfig {

    /** 追评/明细定位：orderId+userId 是其前缀，同一索引可服务于 getOrderReviews 的查询 */
    private static final String INDEX_ORDER_USER_SKU = "idx_order_user_sku";

    /** 商品评价列表：productId 等值 + createdAt 倒序，让排序与分页走索引 */
    private static final String INDEX_PRODUCT_CREATED = "idx_product_created";

    private final MongoTemplate mongoTemplate;

    @PostConstruct
    public void ensureIndexes() {
        // 不因为建索引失败而拒绝启动：byw-review 整体对 Mongo 异常是降级容忍的
        // （findReviewDetail 等读路径都会 catch），按"索引缺失"直接起不来会把影响面放大。
        // 但必须 error 级别且说清后果，否则只是从"没有代码"变成"没人看日志"，等于没改。
        try {
            IndexOperations indexOps = mongoTemplate.indexOps(ReviewDetail.class);
            indexOps.ensureIndex(new Index()
                    .on("orderId", Sort.Direction.ASC)
                    .on("userId", Sort.Direction.ASC)
                    .on("skuId", Sort.Direction.ASC)
                    .named(INDEX_ORDER_USER_SKU));
            indexOps.ensureIndex(new Index()
                    .on("productId", Sort.Direction.ASC)
                    .on("createdAt", Sort.Direction.DESC)
                    .named(INDEX_PRODUCT_CREATED));
            log.info("review_details 索引已确保存在: {}, {}", INDEX_ORDER_USER_SKU, INDEX_PRODUCT_CREATED);
        } catch (Exception e) {
            log.error("review_details 索引创建失败，评价相关查询将退化为全集合扫描，"
                    + "请检查 MongoDB 连通性后重启 byw-review: {}", e.getMessage());
        }
    }
}
