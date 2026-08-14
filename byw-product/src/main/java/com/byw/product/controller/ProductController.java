package com.byw.product.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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

    /** 商品列表（分页 + 排序） */
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

        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Product::getStatus, 1); // 只查上架商品
        wrapper.eq(Product::getAuditStatus, 1); // 且已审核通过
        if (shopId != null) wrapper.eq(Product::getShopId, shopId); // 店铺主页店内筛选

        // 按分类名称查询，包含子分类
        if (category != null && !category.isBlank()) {
            Category cat = categoryService.getOne(new LambdaQueryWrapper<Category>()
                    .eq(Category::getName, category));
            if (cat != null) {
                // 收集该分类及其所有子分类 ID
                List<Long> catIds = new java.util.ArrayList<>();
                catIds.add(cat.getId());
                List<Category> allCats = categoryService.list();
                collectChildIds(cat.getId(), allCats, catIds);
                wrapper.in(Product::getCategoryId, catIds);
            }
        }
        if (brandId != null) wrapper.eq(Product::getBrandId, brandId);
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w.like(Product::getName, keyword)
                    .or().like(Product::getSubtitle, keyword));
        }

        // 价格来自 SKU，无法用 DB 层 orderBy/where 完成，取全量匹配商品后在内存中筛选/排序/分页
        List<Product> allProducts = productService.list(wrapper);

        // 批量查询 SKU 计算最低价
        java.util.Map<Long, BigDecimal> minPriceMap = new java.util.HashMap<>();
        if (!allProducts.isEmpty()) {
            List<Long> productIds = allProducts.stream().map(Product::getId).collect(Collectors.toList());
            List<Sku> allSkus = skuService.list(new LambdaQueryWrapper<Sku>()
                    .in(Sku::getProductId, productIds));
            for (Sku sku : allSkus) {
                minPriceMap.merge(sku.getProductId(), sku.getPrice(),
                        (old, val) -> old.compareTo(val) > 0 ? val : old);
            }
        }

        // 价格区间筛选（基于 SKU 最低价）
        boolean priceFilter = minPrice != null || maxPrice != null;
        List<Product> filtered = allProducts.stream().filter(p -> {
            if (!priceFilter) return true;
            BigDecimal mp = minPriceMap.get(p.getId());
            if (mp == null) return false; // 无 SKU 价，价格过滤时排除
            if (minPrice != null && mp.compareTo(minPrice) < 0) return false;
            if (maxPrice != null && mp.compareTo(maxPrice) > 0) return false;
            return true;
        }).collect(Collectors.toList());

        // 排序（价格排序 null 值排末尾）
        java.util.Comparator<Product> comparator;
        if ("sales".equals(sort)) {
            comparator = java.util.Comparator.comparing(
                    Product::getSalesCount, java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())).reversed();
        } else if ("new".equals(sort)) {
            comparator = java.util.Comparator.comparing(
                    Product::getCreatedAt, java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())).reversed();
        } else if ("price_asc".equals(sort)) {
            comparator = java.util.Comparator.comparing(
                    p -> minPriceMap.get(p.getId()), java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder()));
        } else if ("price_desc".equals(sort)) {
            comparator = java.util.Comparator.comparing(
                    (Product p) -> minPriceMap.get(p.getId()), java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())).reversed();
        } else {
            // default（综合）：销量优先，再按创建时间
            comparator = java.util.Comparator.comparing(
                            Product::getSalesCount, java.util.Comparator.nullsLast(java.util.Comparator.<Integer>naturalOrder())).reversed()
                    .thenComparing(java.util.Comparator.comparing(
                            Product::getCreatedAt, java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())).reversed());
        }
        filtered.sort(comparator);

        long total = filtered.size();

        // 手动分页
        int fromIndex = Math.max(0, (pageNum - 1) * pageSize);
        int toIndex = Math.min(filtered.size(), fromIndex + pageSize);
        List<Product> pageProducts = fromIndex >= filtered.size()
                ? java.util.Collections.emptyList()
                : filtered.subList(fromIndex, toIndex);

        List<ProductDTO> dtoList = pageProducts.stream().map(p -> {
            ProductDTO dto = new ProductDTO();
            BeanUtils.copyProperties(p, dto);
            dto.setMinPrice(minPriceMap.get(p.getId()));
            return dto;
        }).collect(Collectors.toList());

        return R.ok(PageResult.of(dtoList, total, pageNum, pageSize));
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
