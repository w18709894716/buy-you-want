package com.byw.admin.controller;

import com.byw.api.product.ProductFeignClient;
import com.byw.api.product.dto.BrandDTO;
import com.byw.api.product.dto.CategoryDTO;
import com.byw.api.product.dto.ProductDTO;
import com.byw.common.core.result.PageResult;
import com.byw.common.core.result.R;
import com.byw.common.security.annotation.RequireAdmin;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/product")
@RequireAdmin
@RequiredArgsConstructor
public class AdminProductController {

    private final ProductFeignClient productFeignClient;

    // ========== 商品管理 ==========

    @GetMapping("/list")
    public R<PageResult<ProductDTO>> list(@RequestParam(defaultValue = "1") Integer pageNum,
                                          @RequestParam(defaultValue = "10") Integer pageSize,
                                          @RequestParam(required = false) String keyword,
                                          @RequestParam(required = false) Integer status) {
        return productFeignClient.listProducts(pageNum, pageSize, keyword, status);
    }

    @GetMapping("/{productId}")
    public R<ProductDTO> getProductById(@PathVariable Long productId) {
        return productFeignClient.getProductById(productId);
    }

    @PostMapping
    public R<Long> createProduct(@RequestBody ProductDTO productDTO) {
        return productFeignClient.createProduct(productDTO);
    }

    @PutMapping("/{productId}")
    public R<Boolean> updateProduct(@PathVariable Long productId, @RequestBody ProductDTO productDTO) {
        return productFeignClient.updateProduct(productId, productDTO);
    }

    @DeleteMapping("/{productId}")
    public R<Boolean> deleteProduct(@PathVariable Long productId) {
        return productFeignClient.deleteProduct(productId);
    }

    @PutMapping("/{productId}/status")
    public R<Void> toggleProductStatus(@PathVariable Long productId) {
        return productFeignClient.toggleProductStatus(productId);
    }

    // ========== 分类管理 ==========

    @GetMapping("/category/tree")
    public R<List<CategoryDTO>> getCategoryTree() {
        return productFeignClient.getCategoryTree();
    }

    @PostMapping("/category/create")
    public R<Void> createCategory(@RequestBody CategoryDTO dto) {
        return productFeignClient.createCategory(dto);
    }

    @PutMapping("/category/{id}")
    public R<Void> updateCategory(@PathVariable Long id, @RequestBody CategoryDTO dto) {
        return productFeignClient.updateCategory(id, dto);
    }

    @DeleteMapping("/category/{id}")
    public R<Void> deleteCategory(@PathVariable Long id) {
        return productFeignClient.deleteCategory(id);
    }

    // ========== 品牌管理 ==========

    @GetMapping("/brand/list")
    public R<List<BrandDTO>> listBrands(@RequestParam(required = false) String name) {
        return productFeignClient.listBrands(name);
    }

    @PostMapping("/brand/create")
    public R<Void> createBrand(@RequestBody BrandDTO dto) {
        return productFeignClient.createBrand(dto);
    }

    @PutMapping("/brand/{id}")
    public R<Void> updateBrand(@PathVariable Long id, @RequestBody BrandDTO dto) {
        return productFeignClient.updateBrand(id, dto);
    }

    @DeleteMapping("/brand/{id}")
    public R<Void> deleteBrand(@PathVariable Long id) {
        return productFeignClient.deleteBrand(id);
    }

    @PutMapping("/brand/{id}/status")
    public R<Void> toggleBrandStatus(@PathVariable Long id) {
        return productFeignClient.toggleBrandStatus(id);
    }
}
