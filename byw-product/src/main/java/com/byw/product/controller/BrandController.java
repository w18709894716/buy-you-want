package com.byw.product.controller;

import jakarta.validation.Valid;
import com.alicp.jetcache.anno.CacheInvalidate;
import com.byw.common.core.constant.CacheNames;
import com.byw.common.core.result.R;
import com.byw.common.security.annotation.Public;
import com.byw.common.security.annotation.RequirePerm;
import com.byw.product.entity.Brand;
import com.byw.product.service.BrandService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "品牌管理", description = "品牌列表、增删改")
@RestController
@RequestMapping("/brand")
@RequiredArgsConstructor
public class BrandController {

    private final BrandService brandService;

    @Operation(summary = "获取品牌列表")
    @GetMapping("/list")
    @Public
    public R<List<Brand>> listBrands() {
        return R.ok(brandService.listAllBrands());
    }

    @Operation(summary = "新增品牌(管理员)")
    @PostMapping
    @RequirePerm("brand:manage")
    @CacheInvalidate(name = CacheNames.BRAND_ALL, key = "'all'")
    public R<Void> createBrand(@Valid @RequestBody Brand brand) {
        brandService.save(brand);
        return R.ok();
    }

    @Operation(summary = "更新品牌(管理员)")
    @PutMapping
    @RequirePerm("brand:manage")
    @CacheInvalidate(name = CacheNames.BRAND_ALL, key = "'all'")
    public R<Void> updateBrand(@Valid @RequestBody Brand brand) {
        brandService.updateById(brand);
        return R.ok();
    }

    @Operation(summary = "删除品牌(管理员)")
    @DeleteMapping("/{id}")
    @RequirePerm("brand:manage")
    @CacheInvalidate(name = CacheNames.BRAND_ALL, key = "'all'")
    public R<Void> deleteBrand(@PathVariable Long id) {
        brandService.removeById(id);
        return R.ok();
    }
}
