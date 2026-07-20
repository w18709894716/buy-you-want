package com.byw.admin.controller;

import com.byw.api.product.ProductFeignClient;
import com.byw.api.product.dto.BannerDTO;
import com.byw.common.core.result.R;
import com.byw.common.security.annotation.RequireAdmin;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "管理端-轮播Banner", description = "首页轮播图配置管理")
@RestController
@RequestMapping("/admin/banner")
@RequireAdmin
@RequiredArgsConstructor
public class AdminBannerController {

    private final ProductFeignClient productFeignClient;

    @Operation(summary = "轮播图列表")
    @GetMapping("/list")
    public R<List<BannerDTO>> list() {
        return productFeignClient.listBanners();
    }

    @Operation(summary = "新增轮播图")
    @PostMapping("/create")
    public R<Void> create(@RequestBody BannerDTO dto) {
        return productFeignClient.createBanner(dto);
    }

    @Operation(summary = "更新轮播图")
    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @RequestBody BannerDTO dto) {
        return productFeignClient.updateBanner(id, dto);
    }

    @Operation(summary = "删除轮播图")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        return productFeignClient.deleteBanner(id);
    }

    @Operation(summary = "切换轮播图启用状态")
    @PutMapping("/{id}/status")
    public R<Void> toggleStatus(@PathVariable Long id) {
        return productFeignClient.toggleBannerStatus(id);
    }
}
