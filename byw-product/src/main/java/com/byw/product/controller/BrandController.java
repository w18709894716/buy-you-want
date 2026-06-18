package com.byw.product.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.byw.common.core.result.R;
import com.byw.common.security.annotation.RequireAdmin;
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
    public R<List<Brand>> listBrands() {
        LambdaQueryWrapper<Brand> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByAsc(Brand::getSortOrder);
        return R.ok(brandService.list(wrapper));
    }

    @Operation(summary = "新增品牌(管理员)")
    @PostMapping
    @RequireAdmin
    public R<Void> createBrand(@RequestBody Brand brand) {
        brandService.save(brand);
        return R.ok();
    }

    @Operation(summary = "更新品牌(管理员)")
    @PutMapping
    @RequireAdmin
    public R<Void> updateBrand(@RequestBody Brand brand) {
        brandService.updateById(brand);
        return R.ok();
    }

    @Operation(summary = "删除品牌(管理员)")
    @DeleteMapping("/{id}")
    @RequireAdmin
    public R<Void> deleteBrand(@PathVariable Long id) {
        brandService.removeById(id);
        return R.ok();
    }
}
