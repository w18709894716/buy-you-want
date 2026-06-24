package com.byw.admin.controller;

import com.byw.api.product.ProductFeignClient;
import com.byw.api.product.dto.ProductDTO;
import com.byw.common.core.result.PageResult;
import com.byw.common.core.result.R;
import com.byw.common.security.annotation.RequireAdmin;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/product")
@RequireAdmin
@RequiredArgsConstructor
public class AdminProductController {

    private final ProductFeignClient productFeignClient;

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
}
