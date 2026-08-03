package com.byw.admin.controller;

import com.byw.api.product.ProductFeignClient;
import com.byw.api.product.dto.BrandDTO;
import com.byw.api.product.dto.CategoryDTO;
import com.byw.api.product.dto.ProductDTO;
import com.byw.common.core.result.PageResult;
import com.byw.common.core.result.R;
import com.byw.common.security.annotation.RequirePerm;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/product")
@RequiredArgsConstructor
public class AdminProductController {

    private final ProductFeignClient productFeignClient;

    // ========== 商品管理 ==========

    @GetMapping("/list")
    @RequirePerm("product:list")
    public R<PageResult<ProductDTO>> list(@RequestParam(defaultValue = "1") Integer pageNum,
                                          @RequestParam(defaultValue = "10") Integer pageSize,
                                          @RequestParam(required = false) String keyword,
                                          @RequestParam(required = false) Integer status) {
        return productFeignClient.listProducts(pageNum, pageSize, keyword, status, null);
    }

    @GetMapping("/{productId}")
    @RequirePerm("product:list")
    public R<ProductDTO> getProductById(@PathVariable Long productId) {
        return productFeignClient.getProductById(productId);
    }

    // 商品的增/改/删/上下架已迁移至商家后台 byw-merchant（MerchantProductController），
    // 平台侧仅保留商品列表与详情的监管查看能力。

    // ========== 商品审核 ==========

    @GetMapping("/audit/list")
    @RequirePerm("product:audit")
    public R<PageResult<ProductDTO>> auditList(@RequestParam(defaultValue = "1") Integer pageNum,
                                               @RequestParam(defaultValue = "10") Integer pageSize,
                                               @RequestParam(required = false) Integer auditStatus,
                                               @RequestParam(required = false) String keyword) {
        return productFeignClient.listAuditProducts(pageNum, pageSize, auditStatus, keyword);
    }

    @PutMapping("/{productId}/audit")
    @RequirePerm("product:audit")
    public R<Boolean> audit(@PathVariable Long productId,
                            @RequestParam Integer auditStatus,
                            @RequestParam(required = false) String rejectReason) {
        return productFeignClient.auditProduct(productId, auditStatus, rejectReason);
    }

    // ========== 分类管理 ==========

    @GetMapping("/category/tree")
    @RequirePerm("category:manage")
    public R<List<CategoryDTO>> getCategoryTree() {
        return productFeignClient.getCategoryTree();
    }

    @PostMapping("/category/create")
    @RequirePerm("category:manage")
    public R<Void> createCategory(@RequestBody CategoryDTO dto) {
        return productFeignClient.createCategory(dto);
    }

    @PutMapping("/category/{id}")
    @RequirePerm("category:manage")
    public R<Void> updateCategory(@PathVariable Long id, @RequestBody CategoryDTO dto) {
        return productFeignClient.updateCategory(id, dto);
    }

    @DeleteMapping("/category/{id}")
    @RequirePerm("category:manage")
    public R<Void> deleteCategory(@PathVariable Long id) {
        return productFeignClient.deleteCategory(id);
    }

    // ========== 品牌管理 ==========

    @GetMapping("/brand/list")
    @RequirePerm("brand:manage")
    public R<List<BrandDTO>> listBrands(@RequestParam(required = false) String name) {
        return productFeignClient.listBrands(name);
    }

    @PostMapping("/brand/create")
    @RequirePerm("brand:manage")
    public R<Void> createBrand(@RequestBody BrandDTO dto) {
        return productFeignClient.createBrand(dto);
    }

    @PutMapping("/brand/{id}")
    @RequirePerm("brand:manage")
    public R<Void> updateBrand(@PathVariable Long id, @RequestBody BrandDTO dto) {
        return productFeignClient.updateBrand(id, dto);
    }

    @DeleteMapping("/brand/{id}")
    @RequirePerm("brand:manage")
    public R<Void> deleteBrand(@PathVariable Long id) {
        return productFeignClient.deleteBrand(id);
    }

    @PutMapping("/brand/{id}/status")
    @RequirePerm("brand:manage")
    public R<Void> toggleBrandStatus(@PathVariable Long id) {
        return productFeignClient.toggleBrandStatus(id);
    }
}
