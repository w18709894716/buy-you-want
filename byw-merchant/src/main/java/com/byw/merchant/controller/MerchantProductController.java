package com.byw.merchant.controller;

import com.byw.api.product.ProductFeignClient;
import com.byw.api.product.dto.BrandDTO;
import com.byw.api.product.dto.CategoryDTO;
import com.byw.api.product.dto.ProductDTO;
import com.byw.common.core.result.PageResult;
import com.byw.common.core.result.R;
import com.byw.common.security.annotation.RequirePerm;
import com.byw.common.security.context.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 商家端商品管理：仅作用于当前登录商家的店铺（shopId 由上下文强制注入，下游按 shopId 过滤）。
 */
@RestController
@RequestMapping("/merchant/product")
@RequirePerm("m:product:list")
@RequiredArgsConstructor
public class MerchantProductController {

    private final ProductFeignClient productFeignClient;

    @GetMapping("/list")
    public R<PageResult<ProductDTO>> list(@RequestParam(defaultValue = "1") Integer pageNum,
                                          @RequestParam(defaultValue = "10") Integer pageSize,
                                          @RequestParam(required = false) String keyword,
                                          @RequestParam(required = false) Integer status,
                                          @RequestParam(required = false) Integer auditStatus) {
        // 下游 byw-product 依据透传的 X-Shop-Id 过滤，仅返回本店商品
        return productFeignClient.listProducts(pageNum, pageSize, keyword, status, auditStatus);
    }

    @GetMapping("/{productId}")
    public R<ProductDTO> getProductById(@PathVariable Long productId) {
        return productFeignClient.getProductById(productId);
    }

    @PostMapping
    @RequirePerm("m:product:publish")
    public R<Long> createProduct(@RequestBody ProductDTO productDTO) {
        // 强制归属当前商家店铺，防止越权创建
        productDTO.setShopId(UserContext.getShopId());
        return productFeignClient.createProduct(productDTO);
    }

    @PutMapping("/{productId}")
    @RequirePerm("m:product:publish")
    public R<Boolean> updateProduct(@PathVariable Long productId, @RequestBody ProductDTO productDTO) {
        productDTO.setShopId(UserContext.getShopId());
        return productFeignClient.updateProduct(productId, productDTO);
    }

    @DeleteMapping("/{productId}")
    @RequirePerm("m:product:publish")
    public R<Boolean> deleteProduct(@PathVariable Long productId) {
        return productFeignClient.deleteProduct(productId);
    }

    /**
     * 上下架切换
     */
    @PutMapping("/{productId}/status")
    @RequirePerm("m:product:publish")
    public R<Void> toggleProductStatus(@PathVariable Long productId) {
        return productFeignClient.toggleProductStatus(productId);
    }

    /**
     * 提交/重新提交审核（用于驳回后不修改内容直接再次提交）
     */
    @PutMapping("/{productId}/submit")
    @RequirePerm("m:product:publish")
    public R<Boolean> submitForAudit(@PathVariable Long productId) {
        return productFeignClient.submitProductForAudit(productId);
    }

    /**
     * 分类树（平台维护，商家只读，用于发布商品选择分类）
     */
    @GetMapping("/category/tree")
    public R<List<CategoryDTO>> categoryTree() {
        return productFeignClient.getCategoryTree();
    }

    /**
     * 品牌列表（平台维护，商家只读）
     */
    @GetMapping("/brand/list")
    public R<List<BrandDTO>> brandList(@RequestParam(required = false) String name) {
        return productFeignClient.listBrands(name);
    }
}
