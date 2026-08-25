package com.byw.product.es;

import co.elastic.clients.elasticsearch._types.SortOrder;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.byw.common.core.result.PageResult;
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

/**
 * 商品 ES 全文搜索服务：索引维护 + 搜索 + 可用性管理。
 * ES 不可用时 available=false，调用方自动降级 MySQL LIKE 查询。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductEsService implements ApplicationRunner {

    private final ElasticsearchOperations elasticsearchOperations;
    private final ProductMapper productMapper;

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
                    log.info("ES 索引为空，执行初始全量构建");
                    reindexAll();
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

    /** 写路径同步钩子：upsert 商品文档（失败仅告警，不影响主流程） */
    public void upsert(Product product) {
        if (!available || product == null || product.getId() == null) {
            return;
        }
        try {
            elasticsearchOperations.save(toDoc(product));
        } catch (Exception e) {
            log.warn("同步商品到 ES 失败（不影响主流程）: id={}, err={}", product.getId(), e.getMessage());
        }
    }

    /** 写路径同步钩子：删除商品文档（失败仅告警，不影响主流程） */
    public void remove(Long productId) {
        if (!available || productId == null) {
            return;
        }
        try {
            elasticsearchOperations.delete(productId.toString(), IndexCoordinates.of(ProductDoc.INDEX));
        } catch (Exception e) {
            log.warn("从 ES 删除商品失败（不影响主流程）: id={}, err={}", productId, e.getMessage());
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
