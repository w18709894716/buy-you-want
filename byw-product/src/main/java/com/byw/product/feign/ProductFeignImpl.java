package com.byw.product.feign;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.byw.api.product.ProductFeignClient;
import com.byw.api.product.dto.ProductDTO;
import com.byw.api.product.dto.SkuDTO;
import com.byw.api.product.dto.SkuStockDeductDTO;
import com.byw.common.core.result.PageResult;
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
        return R.ok(product.getId());
    }

    @Override
    @PutMapping("/{productId}")
    public R<Boolean> updateProduct(@PathVariable("productId") Long productId, @RequestBody ProductDTO productDTO) {
        Product product = productService.getById(productId);
        if (product == null) return R.fail("商品不存在");
        BeanUtils.copyProperties(productDTO, product);
        product.setId(productId);
        return R.ok(productService.updateById(product));
    }

    @Override
    @DeleteMapping("/{productId}")
    public R<Boolean> deleteProduct(@PathVariable("productId") Long productId) {
        return R.ok(productService.removeById(productId));
    }

    @Override
    @GetMapping("/count")
    public R<Long> countProducts() {
        return R.ok(productService.count());
    }
}
