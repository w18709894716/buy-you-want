package com.byw.product.feign;

import com.byw.api.product.ProductFeignClient;
import com.byw.api.product.dto.ProductDTO;
import com.byw.api.product.dto.SkuDTO;
import com.byw.api.product.dto.SkuStockDeductDTO;
import com.byw.common.core.result.R;
import com.byw.product.entity.Product;
import com.byw.product.entity.Sku;
import com.byw.product.service.ProductService;
import com.byw.product.service.SkuService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/feign/product")
@RequiredArgsConstructor
public class ProductFeignImpl implements ProductFeignClient {

    private final ProductService productService;
    private final SkuService skuService;

    @GetMapping("/{productId}")
    public R<ProductDTO> getProductById(@PathVariable Long productId) {
        Product product = productService.getById(productId);
        if (product == null) return R.fail("商品不存在");
        ProductDTO dto = new ProductDTO();
        BeanUtils.copyProperties(product, dto);
        return R.ok(dto);
    }

    @GetMapping("/sku/{skuId}")
    public R<SkuDTO> getSkuById(@PathVariable Long skuId) {
        Sku sku = skuService.getById(skuId);
        if (sku == null) return R.fail("SKU不存在");
        SkuDTO dto = new SkuDTO();
        BeanUtils.copyProperties(sku, dto);
        return R.ok(dto);
    }

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

    @PostMapping("/sku/deduct-stock")
    public R<Boolean> deductStock(@RequestBody List<SkuStockDeductDTO> deductList) {
        return R.ok(skuService.deductStock(deductList));
    }

    @PostMapping("/sku/release-stock")
    public R<Boolean> releaseStock(@RequestBody List<SkuStockDeductDTO> deductList) {
        return R.ok(skuService.releaseStock(deductList));
    }

    @PostMapping("/sku/lock-stock")
    public R<Boolean> lockStock(@RequestBody List<SkuStockDeductDTO> deductList) {
        return R.ok(skuService.lockStock(deductList));
    }
}
