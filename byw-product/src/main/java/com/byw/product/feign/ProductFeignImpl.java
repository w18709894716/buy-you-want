package com.byw.product.feign;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.byw.api.product.ProductFeignClient;
import com.byw.api.product.dto.BrandDTO;
import com.byw.api.product.dto.CategoryDTO;
import com.byw.api.product.dto.ProductDTO;
import com.byw.api.product.dto.SkuDTO;
import com.byw.api.product.dto.SkuStockDeductDTO;
import com.byw.common.core.result.PageResult;
import com.byw.common.core.result.R;
import com.byw.product.entity.Brand;
import com.byw.product.entity.Category;
import com.byw.product.entity.Product;
import com.byw.product.entity.Sku;
import com.byw.product.mapper.SkuMapper;
import com.byw.product.service.BrandService;
import com.byw.product.service.CategoryService;
import com.byw.product.service.ProductService;
import com.byw.product.service.SkuService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/feign/product")
@RequiredArgsConstructor
public class ProductFeignImpl implements ProductFeignClient {

    private final ProductService productService;
    private final SkuService skuService;
    private final SkuMapper skuMapper;
    private final CategoryService categoryService;
    private final BrandService brandService;

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
        return R.ok(skuService.deductStock(deductList));
    }

    @Override
    @PostMapping("/sku/release-stock")
    public R<Boolean> releaseStock(@RequestBody List<SkuStockDeductDTO> deductList) {
        return R.ok(skuService.releaseStock(deductList));
    }

    @Override
    @PostMapping("/sku/lock-stock")
    public R<Boolean> lockStock(@RequestBody List<SkuStockDeductDTO> deductList) {
        return R.ok(skuService.lockStock(deductList));
    }

    @Override
    @GetMapping("/list")
    public R<PageResult<ProductDTO>> listProducts(@RequestParam(value = "pageNum", defaultValue = "1") Integer pageNum,
                                                  @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize,
                                                  @RequestParam(value = "keyword", required = false) String keyword,
                                                  @RequestParam(value = "status", required = false) Integer status) {
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.like(Product::getName, keyword);
        }
        if (status != null) {
            wrapper.eq(Product::getStatus, status);
        }
        wrapper.orderByDesc(Product::getCreatedAt);
        IPage<Product> page = productService.page(new Page<>(pageNum, pageSize), wrapper);
        List<ProductDTO> dtoList = page.getRecords().stream().map(product -> {
            ProductDTO dto = new ProductDTO();
            BeanUtils.copyProperties(product, dto);
            return dto;
        }).collect(Collectors.toList());
        return R.ok(PageResult.of(dtoList, page.getTotal(), pageNum, pageSize));
    }

    @Override
    @PostMapping
    public R<Long> createProduct(@RequestBody ProductDTO productDTO) {
        Product product = new Product();
        BeanUtils.copyProperties(productDTO, product);
        productService.save(product);
        // 保存 SKU
        saveSkus(product.getId(), productDTO.getSkus());
        return R.ok(product.getId());
    }

    @Override
    @PutMapping("/{productId}")
    public R<Boolean> updateProduct(@PathVariable("productId") Long productId, @RequestBody ProductDTO productDTO) {
        Product product = productService.getById(productId);
        if (product == null) return R.fail("商品不存在");
        BeanUtils.copyProperties(productDTO, product);
        product.setId(productId);
        productService.updateById(product);
        // 更新 SKU：按 specData 匹配，已有则更新，没有则新增
        updateSkus(productId, productDTO.getSkus());
        return R.ok(true);
    }

    /** 更新 SKU 列表（按 product_id + skuCode 匹配已有记录） */
    private void updateSkus(Long productId, List<SkuDTO> skuDTOs) {
        if (skuDTOs == null || skuDTOs.isEmpty()) return;

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
                // 已有：更新属性
                existing.setSpecData(dto.getSpecData());
                existing.setPrice(dto.getPrice());
                existing.setCostPrice(dto.getCostPrice());
                existing.setStock(dto.getStock());
                toUpdate.add(existing);
            } else {
                // 没有：新增
                Sku sku = new Sku();
                sku.setProductId(productId);
                sku.setSkuCode(skuCode);
                sku.setSkuName("SKU " + (i + 1));
                sku.setSpecData(dto.getSpecData());
                sku.setPrice(dto.getPrice());
                sku.setCostPrice(dto.getCostPrice());
                sku.setStock(dto.getStock());
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
    private void saveSkus(Long productId, List<SkuDTO> skuDTOs) {
        if (skuDTOs == null || skuDTOs.isEmpty()) return;
        List<Sku> skuList = new ArrayList<>();
        for (int i = 0; i < skuDTOs.size(); i++) {
            SkuDTO dto = skuDTOs.get(i);
            Sku sku = new Sku();
            sku.setProductId(productId);
            // SKU 编码为空时自动生成
            String skuCode = dto.getSkuCode();
            if (skuCode == null || skuCode.isBlank()) {
                skuCode = "SKU-" + productId + "-" + (i + 1);
            }
            sku.setSkuCode(skuCode);
            sku.setSkuName("SKU " + (i + 1));
            sku.setSpecData(dto.getSpecData());
            sku.setPrice(dto.getPrice());
            sku.setCostPrice(dto.getCostPrice());
            sku.setStock(dto.getStock());
            sku.setStatus(1);
            skuList.add(sku);
        }
        skuService.saveBatch(skuList);
    }

    @Override
    @DeleteMapping("/{productId}")
    public R<Boolean> deleteProduct(@PathVariable("productId") Long productId) {
        return R.ok(productService.removeById(productId));
    }

    @Override
    @PutMapping("/{productId}/status")
    public R<Void> toggleProductStatus(@PathVariable("productId") Long productId) {
        Product product = productService.getById(productId);
        if (product == null) return R.fail("商品不存在");
        // 1=上架 -> 2=下架, 其他 -> 1=上架
        product.setStatus(product.getStatus() == 1 ? 2 : 1);
        productService.updateById(product);
        return R.ok();
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
}
