package com.byw.product.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.byw.api.product.dto.CategoryDTO;
import com.byw.api.product.dto.ProductDTO;
import com.byw.api.product.dto.SkuDTO;
import com.byw.api.shop.ShopFeignClient;
import com.byw.api.shop.dto.ShopDTO;
import com.byw.common.core.result.PageResult;
import com.byw.common.core.result.R;
import com.byw.common.security.annotation.Public;
import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.byw.product.entity.Category;
import com.byw.product.entity.Product;
import com.byw.product.entity.ProductWithPrice;
import com.byw.product.entity.Sku;
import com.byw.product.service.CategoryService;
import com.byw.product.service.ProductService;
import com.byw.product.service.SkuService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;
import java.math.BigDecimal;

/**
 * 用户端商品接口（公开接口，无需登录）
 */
@Slf4j
@RestController
@RequestMapping("/product")
@RequiredArgsConstructor
@Public
public class ProductController {

    private final ProductService productService;
    private final CategoryService categoryService;
    private final SkuService skuService;
    private final ShopFeignClient shopFeignClient;

    /** 分类树 */
    @SentinelResource(value = "category:tree", fallback = "categoryTreeFallback")
    @GetMapping("/category/tree")
    public R<List<CategoryDTO>> getCategoryTree() {
        List<Category> categories = categoryService.getCategoryTree();
        // 转 DTO
        List<CategoryDTO> dtoList = categories.stream().map(c -> {
            CategoryDTO dto = new CategoryDTO();
            BeanUtils.copyProperties(c, dto);
            return dto;
        }).collect(Collectors.toList());
        // 构建树：parentId=0 或 null 为根节点
        java.util.Map<Long, CategoryDTO> map = new java.util.HashMap<>();
        List<CategoryDTO> roots = new java.util.ArrayList<>();
        for (CategoryDTO dto : dtoList) {
            dto.setChildren(new java.util.ArrayList<>());
            map.put(dto.getId(), dto);
        }
        for (CategoryDTO dto : dtoList) {
            if (dto.getParentId() != null && dto.getParentId() != 0 && map.containsKey(dto.getParentId())) {
                map.get(dto.getParentId()).getChildren().add(dto);
            } else {
                roots.add(dto);
            }
        }
        return R.ok(roots);
    }

    /** 分类列表 */
    @GetMapping("/category/list")
    public R<List<CategoryDTO>> getCategoryList() {
        List<Category> categories = categoryService.list();
        List<CategoryDTO> dtoList = categories.stream().map(c -> {
            CategoryDTO dto = new CategoryDTO();
            BeanUtils.copyProperties(c, dto);
            return dto;
        }).collect(Collectors.toList());
        return R.ok(dtoList);
    }

    /** 商品列表（分页 + 排序，DB 层分页，不全量加载） */
    @SentinelResource(value = "product:list", fallback = "productListFallback")
    @GetMapping("/list")
    public R<PageResult<ProductDTO>> getProductList(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "20") Integer pageSize,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Long brandId,
            @RequestParam(required = false) Long shopId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice) {

        // 解析分类（含子分类）ID
        List<Long> catIds = null;
        if (category != null && !category.isBlank()) {
            Category cat = categoryService.getOne(new LambdaQueryWrapper<Category>()
                    .eq(Category::getName, category));
            if (cat != null) {
                catIds = new java.util.ArrayList<>();
                catIds.add(cat.getId());
                collectChildIds(cat.getId(), categoryService.list(), catIds);
            }
        }

        // 关键词：优先 ES 全文匹配（中文分词）；ES 不可用时 service 返回 null 回退 LIKE
        List<Long> esIds = null;
        String likeKeyword = null;
        if (keyword != null && !keyword.isBlank()) {
            esIds = productService.matchProductIdsByEs(keyword);
            if (esIds != null) {
                if (esIds.isEmpty()) {
                    return R.ok(PageResult.of(java.util.Collections.emptyList(), 0L, pageNum, pageSize));
                }
            } else {
                likeKeyword = keyword;
            }
        }

        // 价格筛选/价格排序：SKU 最低价聚合 JOIN + DB 分页，避免全量商品进内存
        boolean priceQuery = minPrice != null || maxPrice != null
                || "price_asc".equals(sort) || "price_desc".equals(sort);
        if (priceQuery) {
            // 排序字段白名单拼接，无注入风险；无 SKU 价商品排末尾（与旧行为一致）
            String orderBy;
            if ("price_asc".equals(sort)) {
                orderBy = "m.min_price IS NULL, m.min_price ASC";
            } else if ("price_desc".equals(sort)) {
                orderBy = "m.min_price IS NULL, m.min_price DESC";
            } else if ("sales".equals(sort)) {
                orderBy = "p.sales_count DESC";
            } else if ("new".equals(sort)) {
                orderBy = "p.created_at DESC";
            } else {
                orderBy = "p.sales_count DESC, p.created_at DESC";
            }
            com.baomidou.mybatisplus.core.metadata.IPage<ProductWithPrice> pricePage =
                    productService.pageWithMinPrice(new Page<>(pageNum, pageSize),
                            shopId, brandId, catIds, esIds, likeKeyword, minPrice, maxPrice, orderBy);
            List<ProductDTO> dtoList = pricePage.getRecords().stream().map(p -> {
                ProductDTO dto = new ProductDTO();
                BeanUtils.copyProperties(p, dto);
                dto.setMinPrice(p.getMinPrice());
                return dto;
            }).collect(Collectors.toList());
            return R.ok(PageResult.of(dtoList, pricePage.getTotal(), pageNum, pageSize));
        }

        // 常规场景：DB 分页（内存占用 O(pageSize)），SKU 最低价只查当页商品
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Product::getStatus, 1); // 只查上架商品
        wrapper.eq(Product::getAuditStatus, 1); // 且已审核通过
        if (shopId != null) wrapper.eq(Product::getShopId, shopId); // 店铺主页店内筛选
        if (catIds != null) wrapper.in(Product::getCategoryId, catIds);
        if (brandId != null) wrapper.eq(Product::getBrandId, brandId);
        if (esIds != null) {
            wrapper.in(Product::getId, esIds);
        } else if (likeKeyword != null) {
            final String kw = likeKeyword;
            wrapper.and(w -> w.like(Product::getName, kw)
                    .or().like(Product::getSubtitle, kw));
        }
        if ("sales".equals(sort)) {
            wrapper.orderByDesc(Product::getSalesCount);
        } else if ("new".equals(sort)) {
            wrapper.orderByDesc(Product::getCreatedAt);
        } else {
            // default（综合）：销量优先，再按创建时间
            wrapper.orderByDesc(Product::getSalesCount).orderByDesc(Product::getCreatedAt);
        }

        Page<Product> result = productService.page(new Page<>(pageNum, pageSize), wrapper);
        List<Product> records = result.getRecords();

        // 只查当页商品的 SKU 最低价（展示用）
        java.util.Map<Long, BigDecimal> minPriceMap = new java.util.HashMap<>();
        if (!records.isEmpty()) {
            List<Long> pageIds = records.stream().map(Product::getId).collect(Collectors.toList());
            skuService.list(new LambdaQueryWrapper<Sku>().in(Sku::getProductId, pageIds))
                    .forEach(sku -> minPriceMap.merge(sku.getProductId(), sku.getPrice(),
                            (old, val) -> old.compareTo(val) > 0 ? val : old));
        }

        List<ProductDTO> dtoList = records.stream().map(p -> {
            ProductDTO dto = new ProductDTO();
            BeanUtils.copyProperties(p, dto);
            dto.setMinPrice(minPriceMap.get(p.getId()));
            return dto;
        }).collect(Collectors.toList());

        return R.ok(PageResult.of(dtoList, result.getTotal(), pageNum, pageSize));
    }

    /** 商品详情 */
    @SentinelResource(value = "product:detail", fallback = "productDetailFallback")
    @GetMapping("/{productId}")
    public R<ProductDTO> getProductDetail(@PathVariable Long productId) {
        Product product = productService.getById(productId);
        if (product == null) return R.fail("商品不存在");
        // 仅对买家展示已上架且审核通过的商品
        if (product.getStatus() == null || product.getStatus() != 1
                || product.getAuditStatus() == null || product.getAuditStatus() != 1) {
            return R.fail("商品不存在或已下架");
        }
        ProductDTO dto = new ProductDTO();
        BeanUtils.copyProperties(product, dto);
        // 加载 SKU
        List<Sku> skuList = skuService.list(new LambdaQueryWrapper<Sku>()
                .eq(Sku::getProductId, productId));
        List<SkuDTO> skuDTOs = skuList.stream().map(sku -> {
            SkuDTO skuDTO = new SkuDTO();
            BeanUtils.copyProperties(sku, skuDTO);
            return skuDTO;
        }).collect(Collectors.toList());
        dto.setSkus(skuDTOs);
        // 计算最低价
        skuList.stream().map(Sku::getPrice)
                .min(java.math.BigDecimal::compareTo)
                .ifPresent(dto::setMinPrice);
        // 回填归属店铺名称，供用户端详情页展示；失败时静默跳过
        if (product.getShopId() != null) {
            try {
                R<ShopDTO> shopResult = shopFeignClient.getShopById(product.getShopId());
                if (shopResult.isSuccess() && shopResult.getData() != null) {
                    dto.setShopName(shopResult.getData().getName());
                }
            } catch (Exception e) {
                log.warn("回填商品店铺名称失败: productId={}, {}", productId, e.getMessage());
            }
        }
        return R.ok(dto);
    }

    /** 店内分类：该店铺上架且审核通过商品的分类分布（平铺，按商品数降序） */
    @GetMapping("/shop/{shopId}/categories")
    public R<List<ShopCategoryVO>> listShopCategories(@PathVariable Long shopId) {
        List<Product> products = productService.list(new LambdaQueryWrapper<Product>()
                .eq(Product::getStatus, 1)
                .eq(Product::getAuditStatus, 1)
                .eq(Product::getShopId, shopId));
        java.util.Map<Long, Long> countByCat = products.stream()
                .filter(p -> p.getCategoryId() != null)
                .collect(Collectors.groupingBy(Product::getCategoryId, Collectors.counting()));
        if (countByCat.isEmpty()) return R.ok(java.util.Collections.emptyList());
        java.util.Map<Long, String> nameMap = categoryService.list().stream()
                .collect(Collectors.toMap(Category::getId, Category::getName, (a, b) -> a));
        List<ShopCategoryVO> result = countByCat.entrySet().stream()
                .map(e -> {
                    ShopCategoryVO vo = new ShopCategoryVO();
                    vo.setCategoryId(e.getKey());
                    vo.setCategoryName(nameMap.getOrDefault(e.getKey(), "其他"));
                    vo.setCount(e.getValue().intValue());
                    return vo;
                })
                .sorted((a, b) -> Integer.compare(b.getCount(), a.getCount()))
                .collect(Collectors.toList());
        return R.ok(result);
    }

    /** 店内分类统计项 */
    @Data
    public static class ShopCategoryVO {
        private Long categoryId;
        private String categoryName;
        private Integer count;
    }

    /** 递归收集子分类 ID */
    private void collectChildIds(Long parentId, List<Category> allCats, List<Long> result) {
        for (Category c : allCats) {
            if (parentId.equals(c.getParentId())) {
                result.add(c.getId());
                collectChildIds(c.getId(), allCats, result);
            }
        }
    }

    // ========== Sentinel fallback ==========
    private R<List<CategoryDTO>> categoryTreeFallback(Throwable ex) {
        return R.fail("系统繁忙，请稍后再试");
    }
    private R<PageResult<ProductDTO>> productListFallback(Integer pageNum, Integer pageSize, String sort, String category, Long brandId, Long shopId, String keyword, BigDecimal minPrice, BigDecimal maxPrice, Throwable ex) {
        return R.fail("系统繁忙，请稍后再试");
    }
    private R<ProductDTO> productDetailFallback(Long productId, Throwable ex) {
        return R.fail("系统繁忙，请稍后再试");
    }
}
