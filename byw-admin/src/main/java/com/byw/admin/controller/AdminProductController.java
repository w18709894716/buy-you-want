package com.byw.admin.controller;

import com.byw.api.product.ProductFeignClient;
import com.byw.api.product.dto.ProductDTO;
import com.byw.common.core.result.R;
import com.byw.common.security.annotation.RequireAdmin;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/admin/product")
@RequireAdmin
@RequiredArgsConstructor
public class AdminProductController {

    private final ProductFeignClient productFeignClient;

    @GetMapping("/list")
    public R<Map<String, Object>> list(@RequestParam(defaultValue = "1") Integer pageNum,
                                        @RequestParam(defaultValue = "10") Integer pageSize) {
        // TODO: 实现商品列表查询
        Map<String, Object> result = new HashMap<>();
        result.put("list", java.util.Collections.emptyList());
        result.put("total", 0);
        return R.ok(result);
    }

    @GetMapping("/{productId}")
    public R<ProductDTO> getProductById(@PathVariable Long productId) {
        return productFeignClient.getProductById(productId);
    }

    @PostMapping
    public R<Long> createProduct(@RequestBody ProductDTO productDTO) {
        // TODO: 实现商品创建
        return R.ok(0L);
    }

    @PutMapping("/{productId}")
    public R<Boolean> updateProduct(@PathVariable Long productId,
                                    @RequestBody ProductDTO productDTO) {
        // TODO: 实现商品更新
        return R.ok(true);
    }
}
