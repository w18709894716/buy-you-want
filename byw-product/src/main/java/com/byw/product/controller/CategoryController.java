package com.byw.product.controller;

import com.byw.common.core.result.R;
import com.byw.common.security.annotation.RequireAdmin;
import com.byw.product.entity.Category;
import com.byw.product.service.CategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "分类管理", description = "分类树、增删改")
@RestController
@RequestMapping("/category")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    @Operation(summary = "获取分类树")
    @GetMapping("/tree")
    public R<List<Category>> getCategoryTree() {
        return R.ok(categoryService.getCategoryTree());
    }

    @Operation(summary = "新增分类(管理员)")
    @PostMapping
    @RequireAdmin
    public R<Void> createCategory(@RequestBody Category category) {
        categoryService.save(category);
        return R.ok();
    }

    @Operation(summary = "更新分类(管理员)")
    @PutMapping
    @RequireAdmin
    public R<Void> updateCategory(@RequestBody Category category) {
        categoryService.updateById(category);
        return R.ok();
    }

    @Operation(summary = "删除分类(管理员)")
    @DeleteMapping("/{id}")
    @RequireAdmin
    public R<Void> deleteCategory(@PathVariable Long id) {
        categoryService.removeById(id);
        return R.ok();
    }
}
