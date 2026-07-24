package com.byw.product.controller;

import com.byw.common.core.result.R;
import com.byw.product.entity.Banner;
import com.byw.product.service.BannerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "轮播Banner", description = "首页轮播图")
@RestController
@RequestMapping("/banner")
@RequiredArgsConstructor
public class BannerController {

    private final BannerService bannerService;

    @Operation(summary = "获取当前生效的轮播图列表")
    @GetMapping("/list")
    public R<List<Banner>> listActive(@RequestParam(required = false) Integer position) {
        return R.ok(bannerService.listActiveBanners(position));
    }
}
