package com.byw.product.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.byw.common.core.result.PageResult;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.byw.product.entity.Product;
import com.byw.product.entity.ProductWithPrice;
import com.byw.product.es.ProductEsService;
import com.byw.product.mapper.ProductMapper;
import com.byw.product.service.ProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductServiceImpl extends ServiceImpl<ProductMapper, Product> implements ProductService {

    private final ProductEsService productEsService;

    @Override
    public PageResult<Product> searchProducts(String keyword, Long categoryId,
                                              BigDecimal minPrice, BigDecimal maxPrice,
                                              String sortBy, Integer pageNum, Integer pageSize) {
        int page = pageNum == null || pageNum < 1 ? 1 : pageNum;
        int size = pageSize == null || pageSize < 1 ? 10 : pageSize;

        // ES 可用时走 ES 全文搜索，异常自动降级 MySQL
        if (productEsService.isAvailable()) {
            try {
                PageResult<Long> idPage = productEsService.search(keyword, categoryId, sortBy, page, size);
                List<Long> ids = idPage.getList();
                if (ids == null || ids.isEmpty()) {
                    return PageResult.of(List.of(), idPage.getTotal(), page, size);
                }
                // 按 ES 命中顺序回 MySQL 取数，保证返回数据实时且与现接口一致
                Map<Long, Product> byId = baseMapper.selectBatchIds(ids).stream()
                        .collect(Collectors.toMap(Product::getId, p -> p));
                List<Product> ordered = ids.stream().map(byId::get).filter(Objects::nonNull).toList();
                log.info("商品搜索走 ES: keyword={}, categoryId={}, total={}", keyword, categoryId, idPage.getTotal());
                return PageResult.of(ordered, idPage.getTotal(), page, size);
            } catch (Exception e) {
                log.warn("ES 搜索不可用，降级 MySQL", e);
                productEsService.markUnavailable();
            }
        }

        // MySQL 降级路径（ES 不可用或异常回退时）
        log.info("商品搜索走 MySQL: keyword={}, esAvailable={}", keyword, productEsService.isAvailable());
        Page<Product> pageObj = new Page<>(page, size);

        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Product::getStatus, 1);
        wrapper.eq(Product::getAuditStatus, 1);

        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(Product::getName, keyword)
                    .or()
                    .like(Product::getSubtitle, keyword));
        }

        if (categoryId != null) {
            wrapper.eq(Product::getCategoryId, categoryId);
        }

        // sort（兼容前端 new / newest 两种传参）
        if ("sales".equals(sortBy)) {
            wrapper.orderByDesc(Product::getSalesCount);
        } else {
            wrapper.orderByDesc(Product::getCreatedAt);
        }

        Page<Product> result = baseMapper.selectPage(pageObj, wrapper);
        return PageResult.of(result.getRecords(), result.getTotal(), page, size);
    }

    @Override
    public List<Long> matchProductIdsByEs(String keyword) {
        if (!productEsService.isAvailable()) {
            return null;
        }
        try {
            List<Long> ids = productEsService.matchIdsByKeyword(keyword);
            log.info("商品关键词匹配走 ES: keyword={}, 命中={}", keyword, ids.size());
            return ids;
        } catch (Exception e) {
            log.warn("ES 关键词匹配不可用，回退 MySQL LIKE", e);
            productEsService.markUnavailable();
            return null;
        }
    }

    @Override
    public com.baomidou.mybatisplus.core.metadata.IPage<ProductWithPrice> pageWithMinPrice(
            com.baomidou.mybatisplus.core.metadata.IPage<ProductWithPrice> page,
            Long shopId, Long brandId, List<Long> categoryIds, List<Long> productIds,
            String keyword, BigDecimal minPrice, BigDecimal maxPrice, String orderBy) {
        return baseMapper.selectPageWithMinPrice(page, shopId, brandId, categoryIds, productIds,
                keyword, minPrice, maxPrice, orderBy);
    }

    @Override
    public Product getProductDetail(Long id) {
        return baseMapper.selectById(id);
    }
}
