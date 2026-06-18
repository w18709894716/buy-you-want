package com.byw.product.controller;

import com.byw.common.core.result.PageResult;
import com.byw.common.core.result.R;
import com.byw.common.security.annotation.RequireAdmin;
import com.byw.product.entity.Product;
import com.byw.product.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@Tag(name = "商品管理", description = "商品搜索、详情、增删改")
@RestController
@RequestMapping("/product")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @Operation(summary = "搜索商品列表")
    @GetMapping("/list")
    public R<PageResult<Product>> searchProducts(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false, defaultValue = "newest") String sortBy,
            @RequestParam(required = false, defaultValue = "1") Integer pageNum,
            @RequestParam(required = false, defaultValue = "10") Integer pageSize) {
        return R.ok(productService.searchProducts(keyword, categoryId, minPrice, maxPrice, sortBy, pageNum, pageSize));
    }

    @Operation(summary = "获取商品详情")
    @GetMapping("/detail/{id}")
    public R<Product> getProductDetail(@PathVariable Long id) {
        return R.ok(productService.getProductDetail(id));
    }

    @Operation(summary = "新增商品(管理员)")
    @PostMapping
    @RequireAdmin
    public R<Void> createProduct(@RequestBody Product product) {
        productService.save(product);
        return R.ok();
    }

    @Operation(summary = "更新商品(管理员)")
    @PutMapping
    @RequireAdmin
    public R<Void> updateProduct(@RequestBody Product product) {
        productService.updateById(product);
        return R.ok();
    }

    @Operation(summary = "删除商品(管理员)")
    @DeleteMapping("/{id}")
    @RequireAdmin
    public R<Void> deleteProduct(@PathVariable Long id) {
        productService.removeById(id);
        return R.ok();
    }
}
