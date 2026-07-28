package com.byw.product.feign;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.byw.api.product.ProductFeignClient;
import com.byw.api.product.dto.BannerDTO;
import com.byw.api.product.dto.BrandDTO;
import com.byw.api.product.dto.CategoryDTO;
import com.byw.api.product.dto.ProductDTO;
import com.byw.api.product.dto.SkuDTO;
import com.byw.api.product.dto.SkuStockDeductDTO;
import com.byw.api.shop.ShopFeignClient;
import com.byw.api.shop.dto.ShopDTO;
import com.byw.common.core.result.PageResult;
import com.byw.common.core.result.R;
import com.byw.common.security.annotation.Public;
import com.byw.product.entity.Brand;
import com.byw.product.entity.Banner;
import com.byw.product.entity.Category;
import com.byw.product.entity.Product;
import com.byw.product.entity.Sku;
import com.byw.product.mapper.SkuMapper;
import com.byw.product.service.BrandService;
import com.byw.product.service.BannerService;
import com.byw.product.service.CategoryService;
import com.byw.product.service.ProductService;
import com.byw.product.service.SkuService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@Slf4j
@RestController
@RequestMapping("/feign/product")
@RequiredArgsConstructor
@Public
public class ProductFeignImpl implements ProductFeignClient {

    private final ProductService productService;
    private final SkuService skuService;
    private final SkuMapper skuMapper;
    private final CategoryService categoryService;
    private final BrandService brandService;
    private final BannerService bannerService;
    private final ShopFeignClient shopFeignClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    @GetMapping("/{productId}")
    public R<ProductDTO> getProductById(@PathVariable Long productId) {
        Product product = productService.getById(productId);
        if (product == null) return R.fail("商品不存在");
        ProductDTO dto = new ProductDTO();
        BeanUtils.copyProperties(product, dto);
        // 加载该商品的 SKU 列表
        List<Sku> skuList = skuService.list(new LambdaQueryWrapper<Sku>()
                .eq(Sku::getProductId, productId));
        List<SkuDTO> skuDTOs = skuList.stream().map(sku -> {
            SkuDTO skuDTO = new SkuDTO();
            BeanUtils.copyProperties(sku, skuDTO);
            return skuDTO;
        }).collect(Collectors.toList());
        dto.setSkus(skuDTOs);
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

    @Override
    @GetMapping("/sku/{skuId}")
    public R<SkuDTO> getSkuById(@PathVariable Long skuId) {
        Sku sku = skuService.getById(skuId);
        if (sku == null) return R.fail("SKU不存在");
        SkuDTO dto = new SkuDTO();
        BeanUtils.copyProperties(sku, dto);
        return R.ok(dto);
    }

    @Override
    @PostMapping("/sku/list")
    public R<List<SkuDTO>> getSkuByIds(@RequestBody List<Long> skuIds) {
        List<Sku> skuList = skuService.listByIds(skuIds);
        List<SkuDTO> dtoList = skuList.stream().map(sku -> {
            SkuDTO dto = new SkuDTO();
            BeanUtils.copyProperties(sku, dto);
            return dto;
        }).collect(Collectors.toList());
        return R.ok(dtoList);
    }

    @Override
    @PostMapping("/sku/deduct-stock")
    public R<Boolean> deductStock(@RequestBody List<SkuStockDeductDTO> deductList) {
        try {
            return R.ok(skuService.deductStock(deductList));
        } catch (Exception e) {
            return R.fail(e.getMessage());
        }
    }

    @Override
    @PostMapping("/increase-sales")
    public R<Boolean> increaseSales(@RequestBody List<SkuStockDeductDTO> items) {
        // 支付成功后累加商品销量：按 SKU 归属商品聚合数量后累加
        java.util.Map<Long, Integer> productQty = new java.util.HashMap<>();
        for (SkuStockDeductDTO dto : items) {
            if (dto.getSkuId() == null || dto.getQuantity() == null || dto.getQuantity() <= 0) continue;
            Sku sku = skuService.getById(dto.getSkuId());
            if (sku == null || sku.getProductId() == null) continue;
            productQty.merge(sku.getProductId(), dto.getQuantity(), Integer::sum);
        }
        productQty.forEach((productId, qty) -> productService.update(
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<Product>()
                        .eq(Product::getId, productId)
                        .setSql("sales_count = IFNULL(sales_count, 0) + " + qty)));
        return R.ok(true);
    }

    @Override
    @PostMapping("/sku/release-stock")
    public R<Boolean> releaseStock(@RequestBody List<SkuStockDeductDTO> deductList) {
        try {
            return R.ok(skuService.releaseStock(deductList));
        } catch (Exception e) {
            return R.fail(e.getMessage());
        }
    }

    @Override
    @PostMapping("/sku/lock-stock")
    public R<Boolean> lockStock(@RequestBody List<SkuStockDeductDTO> deductList) {
        try {
            return R.ok(skuService.lockStock(deductList));
        } catch (Exception e) {
            return R.fail(e.getMessage());
        }
    }

    @Override
    @GetMapping("/list")
    public R<PageResult<ProductDTO>> listProducts(@RequestParam(value = "pageNum", defaultValue = "1") Integer pageNum,
                                                  @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize,
                                                  @RequestParam(value = "keyword", required = false) String keyword,
                                                  @RequestParam(value = "status", required = false) Integer status,
                                                  @RequestParam(value = "auditStatus", required = false) Integer auditStatus) {
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<>();
        // 商家上下文：仅返回本店商品；平台管理员 shopId 为空则不过滤
        Long shopId = com.byw.common.security.context.UserContext.getShopId();
        wrapper.eq(shopId != null, Product::getShopId, shopId);
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.like(Product::getName, keyword);
        }
        if (status != null) {
            wrapper.eq(Product::getStatus, status);
        }
        if (auditStatus != null) {
            wrapper.eq(Product::getAuditStatus, auditStatus);
        }
        wrapper.orderByDesc(Product::getCreatedAt);
        IPage<Product> page = productService.page(new Page<>(pageNum, pageSize), wrapper);
        List<Product> products = page.getRecords();

        // 批量查询 SKU，计算每个商品的最低价与总库存
        java.util.Map<Long, java.math.BigDecimal> minPriceMap = new java.util.HashMap<>();
        java.util.Map<Long, Integer> stockMap = new java.util.HashMap<>();
        if (!products.isEmpty()) {
            List<Long> productIds = products.stream().map(Product::getId).collect(Collectors.toList());
            List<Sku> allSkus = skuService.list(new LambdaQueryWrapper<Sku>()
                    .in(Sku::getProductId, productIds));
            for (Sku sku : allSkus) {
                minPriceMap.merge(sku.getProductId(), sku.getPrice(),
                        (old, val) -> old.compareTo(val) > 0 ? val : old);
                stockMap.merge(sku.getProductId(), sku.getStock() == null ? 0 : sku.getStock(), Integer::sum);
            }
        }

        List<ProductDTO> dtoList = products.stream().map(product -> {
            ProductDTO dto = new ProductDTO();
            BeanUtils.copyProperties(product, dto);
            dto.setMinPrice(minPriceMap.get(product.getId()));
            dto.setTotalStock(stockMap.getOrDefault(product.getId(), 0));
            return dto;
        }).collect(Collectors.toList());
        return R.ok(PageResult.of(dtoList, page.getTotal(), pageNum, pageSize));
    }

    @Override
    @PostMapping
    public R<Long> createProduct(@RequestBody ProductDTO productDTO) {
        Product product = new Product();
        BeanUtils.copyProperties(productDTO, product);
        // 新建商品统一进入待审核，且强制下架，审核通过后商家方可上架
        product.setAuditStatus(0);
        product.setRejectReason(null);
        product.setStatus(2);
        productService.save(product);
        // 保存 SKU（携带归属店铺，保证多租户维度一致）
        saveSkus(product.getId(), product.getShopId(), productDTO.getSkus());
        return R.ok(product.getId());
    }

    @Override
    @PutMapping("/{productId}")
    public R<Boolean> updateProduct(@PathVariable("productId") Long productId, @RequestBody ProductDTO productDTO) {
        Product product = productService.getById(productId);
        if (product == null) return R.fail("商品不存在");
        // 商家上下文：只能改本店商品，且不允许篡改归属店铺
        Long ctxShopId = com.byw.common.security.context.UserContext.getShopId();
        if (ctxShopId != null && !ctxShopId.equals(product.getShopId())) {
            return R.fail("无权操作其他店铺的商品");
        }
        Long originalShopId = product.getShopId();
        BeanUtils.copyProperties(productDTO, product);
        product.setId(productId);
        if (ctxShopId != null) {
            product.setShopId(originalShopId);
            // 商家修改商品内容后需重新审核：重置为待审核并强制下架，清空驳回原因
            product.setAuditStatus(0);
            product.setRejectReason(null);
            product.setStatus(2);
        }
        productService.updateById(product);
        // 更新 SKU：按 specData 匹配，已有则更新，没有则新增
        updateSkus(productId, product.getShopId(), productDTO.getSkus());
        return R.ok(true);
    }

    /** 更新 SKU 列表（按 product_id + skuCode 匹配已有记录） */
    private void updateSkus(Long productId, Long shopId, List<SkuDTO> skuDTOs) {
        if (skuDTOs == null || skuDTOs.isEmpty()) return;

        // 收集前端传来的所有 SKU 编码
        Set<String> incomingCodes = new HashSet<>();
        for (int i = 0; i < skuDTOs.size(); i++) {
            SkuDTO dto = skuDTOs.get(i);
            String skuCode = dto.getSkuCode();
            if (skuCode == null || skuCode.isBlank()) {
                skuCode = "SKU-" + productId + "-" + (i + 1);
            }
            incomingCodes.add(skuCode);
        }

        // 查询当前商品所有未删除的 SKU
        List<Sku> existingSkus = skuService.list(new LambdaQueryWrapper<Sku>()
                .eq(Sku::getProductId, productId));

        // 删除不在前端列表中的 SKU（软删除）
        for (Sku existingSku : existingSkus) {
            if (!incomingCodes.contains(existingSku.getSkuCode())) {
                skuService.removeById(existingSku.getId());
            }
        }

        List<Sku> toInsert = new ArrayList<>();
        List<Sku> toUpdate = new ArrayList<>();

        for (int i = 0; i < skuDTOs.size(); i++) {
            SkuDTO dto = skuDTOs.get(i);
            String skuCode = dto.getSkuCode();
            if (skuCode == null || skuCode.isBlank()) {
                skuCode = "SKU-" + productId + "-" + (i + 1);
            }

            // 按 product_id + skuCode 查已有记录
            Sku existing = skuService.getOne(new LambdaQueryWrapper<Sku>()
                    .eq(Sku::getProductId, productId)
                    .eq(Sku::getSkuCode, skuCode));

            if (existing != null) {
                // 已有：更新属性（同步修正归属店铺，修复存量 SKU shop_id 缺失）
                existing.setShopId(shopId);
                existing.setSpecData(dto.getSpecData());
                existing.setSkuName(buildSkuName(dto.getSpecData()));
                existing.setPrice(dto.getPrice());
                existing.setCostPrice(dto.getCostPrice());
                existing.setStock(dto.getStock());
                if (dto.getImage() != null) existing.setImage(dto.getImage());
                toUpdate.add(existing);
            } else {
                // 没有：新增
                Sku sku = new Sku();
                sku.setProductId(productId);
                sku.setShopId(shopId);
                sku.setSkuCode(skuCode);
                sku.setSkuName(buildSkuName(dto.getSpecData()));
                sku.setSpecData(dto.getSpecData());
                sku.setPrice(dto.getPrice());
                sku.setCostPrice(dto.getCostPrice());
                sku.setStock(dto.getStock());
                sku.setImage(dto.getImage());
                sku.setStatus(1);
                toInsert.add(sku);
            }
        }

        if (!toUpdate.isEmpty()) skuService.updateBatchById(toUpdate);
        if (!toInsert.isEmpty()) {
            // 清理冲突的软删除记录
            Set<String> insertCodes = toInsert.stream()
                    .map(Sku::getSkuCode).collect(Collectors.toSet());
            List<Sku> deletedSkus = skuMapper.selectDeletedByProductId(productId);
            for (Sku deleted : deletedSkus) {
                if (insertCodes.contains(deleted.getSkuCode())) {
                    skuMapper.hardDeleteById(deleted.getId());
                }
            }
            skuService.saveBatch(toInsert);
        }
    }

    /** 保存 SKU 列表 */
    private void saveSkus(Long productId, Long shopId, List<SkuDTO> skuDTOs) {
        if (skuDTOs == null || skuDTOs.isEmpty()) return;
        List<Sku> skuList = new ArrayList<>();
        for (int i = 0; i < skuDTOs.size(); i++) {
            SkuDTO dto = skuDTOs.get(i);
            Sku sku = new Sku();
            sku.setProductId(productId);
            sku.setShopId(shopId);
            String skuCode = dto.getSkuCode();
            if (skuCode == null || skuCode.isBlank()) {
                skuCode = "SKU-" + productId + "-" + (i + 1);
            }
            sku.setSkuCode(skuCode);
            sku.setSkuName(buildSkuName(dto.getSpecData()));
            sku.setSpecData(dto.getSpecData());
            sku.setPrice(dto.getPrice());
            sku.setCostPrice(dto.getCostPrice());
            sku.setStock(dto.getStock());
            sku.setImage(dto.getImage());
            sku.setStatus(1);
            skuList.add(sku);
        }
        skuService.saveBatch(skuList);
    }

    /** 从 specData 生成 SKU 名称，如 {"颜色":"红色","存储":"256GB"} → "红色 256GB" */
    private String buildSkuName(String specData) {
        if (specData == null || specData.isBlank()) return "默认规格";
        try {
            java.util.Map<String, String> specs = objectMapper.readValue(
                    specData, new TypeReference<java.util.Map<String, String>>() {});
            return String.join(" ", specs.values());
        } catch (Exception e) {
            return "默认规格";
        }
    }

    @Override
    @DeleteMapping("/{productId}")
    public R<Boolean> deleteProduct(@PathVariable("productId") Long productId) {
        Long ctxShopId = com.byw.common.security.context.UserContext.getShopId();
        if (ctxShopId != null) {
            Product product = productService.getById(productId);
            if (product == null) return R.fail("商品不存在");
            if (!ctxShopId.equals(product.getShopId())) {
                return R.fail("无权操作其他店铺的商品");
            }
        }
        return R.ok(productService.removeById(productId));
    }

    @Override
    @PutMapping("/{productId}/status")
    public R<Void> toggleProductStatus(@PathVariable("productId") Long productId) {
        Product product = productService.getById(productId);
        if (product == null) return R.fail("商品不存在");
        Long ctxShopId = com.byw.common.security.context.UserContext.getShopId();
        if (ctxShopId != null && !ctxShopId.equals(product.getShopId())) {
            return R.fail("无权操作其他店铺的商品");
        }
        // 1=上架 -> 2=下架, 其他 -> 1=上架
        boolean willOnShelf = product.getStatus() == null || product.getStatus() != 1;
        // 上架前必须已审核通过
        if (willOnShelf && (product.getAuditStatus() == null || product.getAuditStatus() != 1)) {
            return R.fail("商品未通过审核，无法上架");
        }
        product.setStatus(willOnShelf ? 1 : 2);
        productService.updateById(product);
        return R.ok();
    }

    @Override
    @GetMapping("/audit/list")
    public R<PageResult<ProductDTO>> listAuditProducts(@RequestParam(value = "pageNum", defaultValue = "1") Integer pageNum,
                                                       @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize,
                                                       @RequestParam(value = "auditStatus", required = false) Integer auditStatus,
                                                       @RequestParam(value = "keyword", required = false) String keyword) {
        // 平台审核视图：不按店铺过滤
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<>();
        if (auditStatus != null) {
            wrapper.eq(Product::getAuditStatus, auditStatus);
        }
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.like(Product::getName, keyword);
        }
        // 待审核优先，其余按创建时间倒序
        wrapper.orderByAsc(Product::getAuditStatus).orderByDesc(Product::getCreatedAt);
        IPage<Product> page = productService.page(new Page<>(pageNum, pageSize), wrapper);
        List<ProductDTO> dtoList = page.getRecords().stream().map(product -> {
            ProductDTO dto = new ProductDTO();
            BeanUtils.copyProperties(product, dto);
            return dto;
        }).collect(Collectors.toList());
        return R.ok(PageResult.of(dtoList, page.getTotal(), pageNum, pageSize));
    }

    @Override
    @PutMapping("/{productId}/audit")
    public R<Boolean> auditProduct(@PathVariable("productId") Long productId,
                                   @RequestParam("auditStatus") Integer auditStatus,
                                   @RequestParam(value = "rejectReason", required = false) String rejectReason) {
        Product product = productService.getById(productId);
        if (product == null) return R.fail("商品不存在");
        if (auditStatus == null || (auditStatus != 1 && auditStatus != 2)) {
            return R.fail("审核状态非法");
        }
        product.setAuditStatus(auditStatus);
        if (auditStatus == 2) {
            // 驳回：记录原因并强制下架
            product.setRejectReason(rejectReason);
            product.setStatus(2);
        } else {
            // 通过：清空驳回原因（是否上架由商家自行决定）
            product.setRejectReason(null);
        }
        productService.updateById(product);
        return R.ok(true);
    }

    @Override
    @PutMapping("/{productId}/submit")
    public R<Boolean> submitProductForAudit(@PathVariable("productId") Long productId) {
        Product product = productService.getById(productId);
        if (product == null) return R.fail("商品不存在");
        Long ctxShopId = com.byw.common.security.context.UserContext.getShopId();
        if (ctxShopId != null && !ctxShopId.equals(product.getShopId())) {
            return R.fail("无权操作其他店铺的商品");
        }
        // 重新提交审核：重置为待审核，清空驳回原因（保持下架直至审核通过后上架）
        product.setAuditStatus(0);
        product.setRejectReason(null);
        product.setStatus(2);
        productService.updateById(product);
        return R.ok(true);
    }

    @Override
    @GetMapping("/count")
    public R<Long> countProducts() {
        return R.ok(productService.count());
    }

    // ========== 分类管理 ==========

    @Override
    @GetMapping("/category/tree")
    public R<List<CategoryDTO>> getCategoryTree() {
        List<Category> categories = categoryService.getCategoryTree();
        List<CategoryDTO> dtoList = categories.stream().map(c -> {
            CategoryDTO dto = new CategoryDTO();
            BeanUtils.copyProperties(c, dto);
            return dto;
        }).collect(Collectors.toList());
        return R.ok(dtoList);
    }

    @Override
    @PostMapping("/category")
    public R<Void> createCategory(@RequestBody CategoryDTO dto) {
        Category category = new Category();
        BeanUtils.copyProperties(dto, category);
        // 根据 parentId 计算 level
        if (category.getParentId() != null && category.getParentId() > 0) {
            Category parent = categoryService.getById(category.getParentId());
            if (parent != null) {
                category.setLevel(parent.getLevel() + 1);
            }
        } else {
            category.setLevel(1);
        }
        categoryService.save(category);
        return R.ok();
    }

    @Override
    @PutMapping("/category/{categoryId}")
    public R<Void> updateCategory(@PathVariable("categoryId") Long categoryId, @RequestBody CategoryDTO dto) {
        Category category = categoryService.getById(categoryId);
        if (category == null) return R.fail("分类不存在");
        BeanUtils.copyProperties(dto, category);
        category.setId(categoryId);
        categoryService.updateById(category);
        return R.ok();
    }

    @Override
    @DeleteMapping("/category/{categoryId}")
    public R<Void> deleteCategory(@PathVariable("categoryId") Long categoryId) {
        // 检查是否有子分类
        long childCount = categoryService.count(new LambdaQueryWrapper<Category>()
                .eq(Category::getParentId, categoryId));
        if (childCount > 0) return R.fail("该分类下有子分类，无法删除");
        categoryService.removeById(categoryId);
        return R.ok();
    }

    // ========== 品牌管理 ==========

    @Override
    @GetMapping("/brand/list")
    public R<List<BrandDTO>> listBrands(@RequestParam(value = "name", required = false) String name) {
        LambdaQueryWrapper<Brand> wrapper = new LambdaQueryWrapper<>();
        if (name != null && !name.isEmpty()) {
            wrapper.like(Brand::getName, name);
        }
        wrapper.orderByAsc(Brand::getSortOrder);
        List<Brand> brands = brandService.list(wrapper);
        List<BrandDTO> dtoList = brands.stream().map(b -> {
            BrandDTO dto = new BrandDTO();
            BeanUtils.copyProperties(b, dto);
            // 统计该品牌下的商品数
            long count = productService.count(new LambdaQueryWrapper<Product>()
                    .eq(Product::getBrandId, b.getId()));
            dto.setProductCount((int) count);
            return dto;
        }).collect(Collectors.toList());
        return R.ok(dtoList);
    }

    @Override
    @PostMapping("/brand")
    public R<Void> createBrand(@RequestBody BrandDTO dto) {
        Brand brand = new Brand();
        BeanUtils.copyProperties(dto, brand);
        brandService.save(brand);
        return R.ok();
    }

    @Override
    @PutMapping("/brand/{brandId}")
    public R<Void> updateBrand(@PathVariable("brandId") Long brandId, @RequestBody BrandDTO dto) {
        Brand brand = brandService.getById(brandId);
        if (brand == null) return R.fail("品牌不存在");
        BeanUtils.copyProperties(dto, brand);
        brand.setId(brandId);
        brandService.updateById(brand);
        return R.ok();
    }

    @Override
    @DeleteMapping("/brand/{brandId}")
    public R<Void> deleteBrand(@PathVariable("brandId") Long brandId) {
        brandService.removeById(brandId);
        return R.ok();
    }

    @Override
    @PutMapping("/brand/{brandId}/status")
    public R<Void> toggleBrandStatus(@PathVariable("brandId") Long brandId) {
        Brand brand = brandService.getById(brandId);
        if (brand == null) return R.fail("品牌不存在");
        brand.setStatus(brand.getStatus() == 1 ? 0 : 1);
        brandService.updateById(brand);
        return R.ok();
    }

    // ========== 轮播Banner管理 ==========

    @Override
    @GetMapping("/banner/list")
    public R<List<BannerDTO>> listBanners() {
        List<Banner> banners = bannerService.list(new LambdaQueryWrapper<Banner>()
                .orderByAsc(Banner::getSortOrder)
                .orderByDesc(Banner::getId));
        List<BannerDTO> dtoList = banners.stream().map(b -> {
            BannerDTO dto = new BannerDTO();
            BeanUtils.copyProperties(b, dto);
            return dto;
        }).collect(Collectors.toList());
        return R.ok(dtoList);
    }

    @Override
    @PostMapping("/banner")
    public R<Void> createBanner(@RequestBody BannerDTO dto) {
        Banner banner = new Banner();
        BeanUtils.copyProperties(dto, banner);
        bannerService.save(banner);
        return R.ok();
    }

    @Override
    @PutMapping("/banner/{bannerId}")
    public R<Void> updateBanner(@PathVariable("bannerId") Long bannerId, @RequestBody BannerDTO dto) {
        Banner banner = bannerService.getById(bannerId);
        if (banner == null) return R.fail("Banner不存在");
        BeanUtils.copyProperties(dto, banner);
        banner.setId(bannerId);
        bannerService.updateById(banner);
        return R.ok();
    }

    @Override
    @DeleteMapping("/banner/{bannerId}")
    public R<Void> deleteBanner(@PathVariable("bannerId") Long bannerId) {
        bannerService.removeById(bannerId);
        return R.ok();
    }

    @Override
    @PutMapping("/banner/{bannerId}/status")
    public R<Void> toggleBannerStatus(@PathVariable("bannerId") Long bannerId) {
        Banner banner = bannerService.getById(bannerId);
        if (banner == null) return R.fail("Banner不存在");
        banner.setStatus(banner.getStatus() == 1 ? 0 : 1);
        bannerService.updateById(banner);
        return R.ok();
    }
}
