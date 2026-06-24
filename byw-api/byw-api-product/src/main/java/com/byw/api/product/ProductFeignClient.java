package com.byw.api.product;

import com.byw.api.product.dto.ProductDTO;
import com.byw.api.product.dto.SkuDTO;
import com.byw.api.product.dto.SkuStockDeductDTO;
import com.byw.common.core.result.PageResult;
import com.byw.common.core.result.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@FeignClient(name = "byw-product", contextId = "productFeignClient")
public interface ProductFeignClient {

    @GetMapping("/feign/product/{productId}")
    R<ProductDTO> getProductById(@PathVariable("productId") Long productId);

    @GetMapping("/feign/product/sku/{skuId}")
    R<SkuDTO> getSkuById(@PathVariable("skuId") Long skuId);

    @PostMapping("/feign/product/sku/list")
    R<List<SkuDTO>> getSkuByIds(@RequestBody List<Long> skuIds);

    @PostMapping("/feign/product/sku/deduct-stock")
    R<Boolean> deductStock(@RequestBody List<SkuStockDeductDTO> deductList);

    @PostMapping("/feign/product/sku/release-stock")
    R<Boolean> releaseStock(@RequestBody List<SkuStockDeductDTO> deductList);

    @PostMapping("/feign/product/sku/lock-stock")
    R<Boolean> lockStock(@RequestBody List<SkuStockDeductDTO> deductList);

    @GetMapping("/feign/product/list")
    R<PageResult<ProductDTO>> listProducts(@RequestParam(value = "pageNum", defaultValue = "1") Integer pageNum,
                                           @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize,
                                           @RequestParam(value = "keyword", required = false) String keyword,
                                           @RequestParam(value = "status", required = false) Integer status);

    @PostMapping("/feign/product")
    R<Long> createProduct(@RequestBody ProductDTO productDTO);

    @PutMapping("/feign/product/{productId}")
    R<Boolean> updateProduct(@PathVariable("productId") Long productId, @RequestBody ProductDTO productDTO);

    @DeleteMapping("/feign/product/{productId}")
    R<Boolean> deleteProduct(@PathVariable("productId") Long productId);

    @GetMapping("/feign/product/count")
    R<Long> countProducts();
}
