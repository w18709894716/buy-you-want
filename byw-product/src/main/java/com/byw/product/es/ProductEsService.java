package com.byw.product.es;

import co.elastic.clients.elasticsearch._types.SortOrder;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.byw.common.core.result.PageResult;
import com.byw.common.redis.lock.DistributedLock;
import com.byw.common.redis.util.RedisUtil;
import com.byw.product.entity.Product;
import com.byw.product.mapper.ProductMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.client.elc.NativeQueryBuilder;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.document.Document;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.query.Query;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 商品 ES 全文搜索服务：索引维护 + 搜索 + 可用性管理。
 * ES 不可用时 available=false，调用方自动降级 MySQL LIKE 查询。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductEsService implements ApplicationRunner {

    /** 分布式锁 key：多实例同时启动时只让一个节点做全量重建 */
    private static final String LOCK_ES_REINDEX = "byw:lock:task:product-es-reindex";

    /** 分布式锁 key：补同步同一时刻只让一个节点执行 */
    private static final String LOCK_ES_SYNC = "byw:lock:task:product-es-sync";

    /** 待补同步的商品 id 集合：写入时登记，同步成功后移除 */
    private static final String ES_DIRTY_SET = "byw:es:dirty:product";

    /** 单轮补同步的批量上限：ES 长时间不可用时避免一次拉取过多 */
    private static final int ES_SYNC_BATCH_SIZE = 200;

    private final ElasticsearchOperations elasticsearchOperations;
    private final ProductMapper productMapper;
    private final DistributedLock distributedLock;
    private final RedisUtil redisUtil;

    /** ES 可用性标记：false 时商品搜索走 MySQL */
    private volatile boolean available = false;

    public boolean isAvailable() {
        return available;
    }

    public void markUnavailable() {
        this.available = false;
    }

    @Override
    public void run(ApplicationArguments args) {
        refreshAvailability();
        if (available) {
            log.info("ES 探测成功，商品搜索已启用 Elasticsearch: index={}", ProductDoc.INDEX);
            try {
                long docs = elasticsearchOperations.count(Query.findAll(), ProductDoc.class);
                if (docs == 0) {
                    // 多实例同时启动时只让一个实例重建索引，避免重复拉全表写 ES
                    distributedLock.tryExecuteWithLock(LOCK_ES_REINDEX, 0, TimeUnit.SECONDS, () -> {
                        log.info("ES 索引为空，执行初始全量构建");
                        reindexAll();
                    });
                }
            } catch (Exception e) {
                log.warn("ES 初始全量构建失败: {}", e.getMessage());
            }
        } else {
            log.warn("启动时 ES 不可用，商品搜索暂时走 MySQL，每 30 秒自动重新探测");
        }
    }

    /** 定时探测 ES 并确保索引存在；失败置不可用（搜索自动降级 MySQL） */
    @Scheduled(fixedDelay = 30_000)
    public void refreshAvailability() {
        try {
            ensureIndex();
            if (!available) {
                log.info("ES 已恢复，商品搜索切换为 Elasticsearch");
            }
            available = true;
            drainPendingSync();
        } catch (Throwable e) {
            if (available) {
                log.warn("ES 变为不可用，商品搜索降级 MySQL: {}", e.getMessage());
            } else {
                log.warn("ES 探测失败，商品搜索继续走 MySQL: {}", e.getMessage());
            }
            available = false;
        }
    }

    /** 索引不存在则创建：优先 IK 分词，缺 IK 插件时回退 standard 分词 */
    private void ensureIndex() {
        IndexOperations ops = elasticsearchOperations.indexOps(IndexCoordinates.of(ProductDoc.INDEX));
        if (Boolean.TRUE.equals(ops.exists())) {
            return;
        }
        Map<String, Object> settings = Map.of("number_of_shards", "1", "number_of_replicas", "0");
        try {
            ops.create(settings, Document.parse(mappingJson("ik_max_word")));
            log.info("ES 索引已创建（IK 分词）: {}", ProductDoc.INDEX);
        } catch (Exception e) {
            log.warn("IK 分词建索引失败，回退 standard 分词: {}", e.getMessage());
            if (Boolean.TRUE.equals(ops.exists())) {
                ops.delete();
            }
            ops.create(settings, Document.parse(mappingJson("standard")));
            log.info("ES 索引已创建（standard 分词）: {}", ProductDoc.INDEX);
        }
    }

    private String mappingJson(String analyzer) {
        return """
                {
                  "properties": {
                    "id": {"type": "long"},
                    "name": {"type": "text", "analyzer": "%s"},
                    "subtitle": {"type": "text", "analyzer": "%s"},
                    "shopId": {"type": "long"},
                    "categoryId": {"type": "long"},
                    "salesCount": {"type": "integer"},
                    "status": {"type": "integer"},
                    "auditStatus": {"type": "integer"},
                    "createdAt": {"type": "long"}
                  }
                }
                """.formatted(analyzer, analyzer);
    }

    /**
     * 写路径同步钩子：upsert 商品文档。
     *
     * <p>先登记进"待补同步"集合，再尝试立即写入：即使此刻 ES 不可用或写入失败，
     * 30 秒的定时任务也会把它补上。原先这里直接 return / 只告警，
     * 那些写入会**永久留在索引之外**，只能人工调 {@code /product/es/reindex} 全量重建。
     */
    public void upsert(Product product) {
        if (product == null || product.getId() == null) {
            return;
        }
        markPendingSync(product.getId());
        if (!available) {
            return;
        }
        try {
            elasticsearchOperations.save(toDoc(product));
            clearPendingSync(product.getId());
        } catch (Exception e) {
            log.warn("同步商品到 ES 失败，已登记待补同步: id={}, err={}", product.getId(), e.getMessage());
        }
    }

    /** 写路径同步钩子：删除商品文档（同样登记待补同步，保证"删除"也最终一致） */
    public void remove(Long productId) {
        if (productId == null) {
            return;
        }
        markPendingSync(productId);
        if (!available) {
            return;
        }
        try {
            elasticsearchOperations.delete(productId.toString(), IndexCoordinates.of(ProductDoc.INDEX));
            clearPendingSync(productId);
        } catch (Exception e) {
            log.warn("从 ES 删除商品失败，已登记待补同步: id={}, err={}", productId, e.getMessage());
        }
    }

    /**
     * 补同步"ES 不可用期间被跳过"或"写入失败"的商品。
     *
     * <p>以数据库当前状态为准：查得到就 upsert、查不到（已逻辑删除）就从索引删除，
     * 因此每轮跑完都会向数据库状态收敛，不需要人工干预。
     */
    private void drainPendingSync() {
        try {
            Set<Object> pending = redisUtil.sMembers(ES_DIRTY_SET);
            if (pending == null || pending.isEmpty()) {
                return;
            }
            List<Long> ids = pending.stream()
                    .map(value -> {
                        try {
                            return Long.valueOf(String.valueOf(value));
                        } catch (NumberFormatException ignored) {
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .limit(ES_SYNC_BATCH_SIZE)
                    .toList();
            // 多实例同时运行时交给分布式锁：拿不到锁的实例下一轮再补
            distributedLock.tryExecuteWithLock(LOCK_ES_SYNC, 0, TimeUnit.SECONDS, () -> {
                int success = 0;
                int failed = 0;
                for (Long id : ids) {
                    try {
                        Product product = productMapper.selectById(id);
                        if (product == null) {
                            elasticsearchOperations.delete(id.toString(), IndexCoordinates.of(ProductDoc.INDEX));
                        } else {
                            elasticsearchOperations.save(toDoc(product));
                        }
                        clearPendingSync(id);
                        success++;
                    } catch (Exception e) {
                        // 保留在集合中，下一轮继续重试
                        failed++;
                        log.warn("补同步商品到 ES 失败，保留待重试: id={}, err={}", id, e.getMessage());
                    }
                }
                log.info("ES 补同步完成: 成功={}, 失败={}, 本轮处理={}", success, failed, ids.size());
            });
        } catch (Exception e) {
            // 补同步失败只影响索引新鲜度，不影响搜索降级，因此不向上抛
            log.warn("ES 补同步任务异常（搜索降级不受影响）: {}", e.getMessage());
        }
    }

    private void markPendingSync(Long productId) {
        try {
            redisUtil.sAdd(ES_DIRTY_SET, productId);
        } catch (Exception e) {
            log.warn("登记待补同步商品失败: id={}, err={}", productId, e.getMessage());
        }
    }

    private void clearPendingSync(Long productId) {
        try {
            redisUtil.sRemove(ES_DIRTY_SET, productId);
        } catch (Exception e) {
            log.warn("清除待补同步标记失败: id={}, err={}", productId, e.getMessage());
        }
    }

    /** 全量重建索引：分页扫 MySQL 未删除商品 */
    public int reindexAll() {
        ensureIndex();
        int count = 0;
        int pageNum = 1;
        int pageSize = 500;
        while (true) {
            Page<Product> page = productMapper.selectPage(new Page<>(pageNum, pageSize),
                    new LambdaQueryWrapper<Product>().orderByAsc(Product::getId));
            List<Product> records = page.getRecords();
            if (records.isEmpty()) {
                break;
            }
            elasticsearchOperations.save(records.stream().map(this::toDoc).toList());
            count += records.size();
            if (records.size() < pageSize) {
                break;
            }
            pageNum++;
        }
        log.info("ES 索引全量重建完成: count={}", count);
        return count;
    }

    /**
     * ES 搜索：keyword 走 multi_match(name^2, subtitle)，filter 营业+审核通过+分类，
     * 排序 default=相关度、sales=销量倒序、new/newest=创建时间倒序；返回 id 列表 + 总数。
     */
    public PageResult<Long> search(String keyword, Long categoryId, String sortBy, int pageNum, int pageSize) {
        NativeQueryBuilder builder = NativeQuery.builder()
                .withQuery(q -> q.bool(b -> {
                    if (keyword != null && !keyword.isBlank()) {
                        b.must(m -> m.multiMatch(mm -> mm.query(keyword.trim()).fields("name^2", "subtitle")));
                    }
                    b.filter(f -> f.term(t -> t.field("status").value(1)));
                    b.filter(f -> f.term(t -> t.field("auditStatus").value(1)));
                    if (categoryId != null) {
                        b.filter(f -> f.term(t -> t.field("categoryId").value(categoryId)));
                    }
                    return b;
                }));
        if ("sales".equals(sortBy)) {
            builder.withSort(s -> s.field(f -> f.field("salesCount").order(SortOrder.Desc)));
        } else if ("new".equals(sortBy) || "newest".equals(sortBy)) {
            builder.withSort(s -> s.field(f -> f.field("createdAt").order(SortOrder.Desc)));
        }
        NativeQuery query = builder.build();
        query.setPageable(PageRequest.of(pageNum - 1, pageSize));

        SearchHits<ProductDoc> hits = elasticsearchOperations.search(query, ProductDoc.class,
                IndexCoordinates.of(ProductDoc.INDEX));
        List<Long> ids = hits.getSearchHits().stream().map(h -> h.getContent().getId()).toList();
        return PageResult.of(ids, hits.getTotalHits(), pageNum, pageSize);
    }

    /**
     * ES 关键词匹配：返回营业+审核通过商品的 id（相关度排序，最多 1000 个），
     * 供 /product/list 替换 MySQL LIKE；异常由调用方捕获降级。
     */
    public List<Long> matchIdsByKeyword(String keyword) {
        NativeQuery query = NativeQuery.builder()
                .withQuery(q -> q.bool(b -> {
                    b.must(m -> m.multiMatch(mm -> mm.query(keyword.trim()).fields("name^2", "subtitle")));
                    b.filter(f -> f.term(t -> t.field("status").value(1)));
                    b.filter(f -> f.term(t -> t.field("auditStatus").value(1)));
                    return b;
                }))
                .build();
        query.setPageable(PageRequest.of(0, 1000));
        SearchHits<ProductDoc> hits = elasticsearchOperations.search(query, ProductDoc.class,
                IndexCoordinates.of(ProductDoc.INDEX));
        return hits.getSearchHits().stream().map(h -> h.getContent().getId()).toList();
    }

    private ProductDoc toDoc(Product product) {
        ProductDoc doc = new ProductDoc();
        doc.setId(product.getId());
        doc.setName(product.getName());
        doc.setSubtitle(product.getSubtitle());
        doc.setShopId(product.getShopId());
        doc.setCategoryId(product.getCategoryId());
        doc.setSalesCount(product.getSalesCount());
        doc.setStatus(product.getStatus());
        doc.setAuditStatus(product.getAuditStatus());
        doc.setCreatedAt(product.getCreatedAt() == null ? null
                : product.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
        return doc;
    }
}
