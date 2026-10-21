package com.byw.product.controller;

import com.byw.common.core.result.R;
import com.byw.common.security.annotation.RequireAdmin;
import com.byw.product.es.ProductEsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 商品 ES 索引运维接口（需登录态，供管理端/运维手动对齐索引）。
 */
@Slf4j
@RestController
@RequestMapping("/product/es")
@RequiredArgsConstructor
public class ProductEsController {

    private final ProductEsService productEsService;

    /** 全量重建商品索引（仅平台管理员：会扫全表并批量写 ES，不能对普通登录用户开放） */
    @RequireAdmin
    @PostMapping("/reindex")
    public R<Integer> reindex() {
        return R.ok(productEsService.reindexAll());
    }
}
