package com.byw.im.controller;

import com.byw.common.core.result.R;
import com.byw.common.security.annotation.RequireLogin;
import com.byw.common.security.context.UserContext;
import com.byw.im.dto.FaqOptionDTO;
import com.byw.im.entity.Faq;
import com.byw.im.service.FaqService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * IM FAQ 知识库管理 REST 接口（商家端）
 */
@Tag(name = "客服FAQ")
@RestController
@RequestMapping("/im/faq")
@RequiredArgsConstructor
public class ImFaqController {

    private final FaqService faqService;

    @Operation(summary = "买家端FAQ引导选项（按店铺返回启用问题，答案由机器人回复下发）")
    @RequireLogin
    @GetMapping("/options")
    public R<List<FaqOptionDTO>> options(@RequestParam Long shopId) {
        if (shopId == null) {
            return R.fail("shopId 不能为空");
        }
        return R.ok(faqService.listOptions(shopId));
    }

    @Operation(summary = "FAQ列表")
    @RequireLogin
    @GetMapping("/list")
    public R<List<Faq>> list() {
        Long shopId = UserContext.getShopId();
        if (shopId == null) return R.fail("仅商家端可用");
        return R.ok(faqService.listByShop(shopId));
    }

    @Operation(summary = "新增FAQ")
    @RequireLogin
    @PostMapping
    public R<Faq> create(@RequestBody Faq faq) {
        Long shopId = UserContext.getShopId();
        if (shopId == null) return R.fail("仅商家端可用");
        faq.setShopId(shopId);
        return R.ok(faqService.create(faq));
    }

    @Operation(summary = "更新FAQ")
    @RequireLogin
    @PutMapping
    public R<Faq> update(@RequestBody Faq faq) {
        Long shopId = UserContext.getShopId();
        if (shopId == null) return R.fail("仅商家端可用");
        faq.setShopId(shopId);
        return R.ok(faqService.update(faq));
    }

    @Operation(summary = "删除FAQ")
    @RequireLogin
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        Long shopId = UserContext.getShopId();
        if (shopId == null) return R.fail("仅商家端可用");
        faqService.delete(id, shopId);
        return R.ok();
    }

    @Operation(summary = "切换FAQ状态")
    @RequireLogin
    @PutMapping("/{id}/status")
    public R<Void> toggleStatus(@PathVariable Long id, @RequestParam Integer status) {
        Faq faq = new Faq();
        faq.setId(id);
        faq.setStatus(status);
        faqService.update(faq);
        return R.ok();
    }
}