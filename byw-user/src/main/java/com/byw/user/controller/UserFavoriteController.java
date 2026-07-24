package com.byw.user.controller;

import com.byw.common.core.result.PageResult;
import com.byw.common.core.result.R;
import com.byw.common.security.annotation.RequireLogin;
import com.byw.common.security.context.UserContext;
import com.byw.user.service.UserFavoriteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "商品收藏", description = "用户商品收藏管理")
@RestController
@RequestMapping("/user/favorite")
@RequiredArgsConstructor
@RequireLogin
public class UserFavoriteController {

    private final UserFavoriteService userFavoriteService;

    @Operation(summary = "添加收藏")
    @PostMapping("/{productId}")
    public R<Void> add(@PathVariable Long productId) {
        userFavoriteService.addFavorite(UserContext.getUserId(), productId);
        return R.ok();
    }

    @Operation(summary = "取消收藏")
    @DeleteMapping("/{productId}")
    public R<Void> remove(@PathVariable Long productId) {
        userFavoriteService.removeFavorite(UserContext.getUserId(), productId);
        return R.ok();
    }

    @Operation(summary = "是否已收藏")
    @GetMapping("/check/{productId}")
    public R<Boolean> check(@PathVariable Long productId) {
        return R.ok(userFavoriteService.isFavorite(UserContext.getUserId(), productId));
    }

    @Operation(summary = "已收藏的商品ID列表")
    @GetMapping("/ids")
    public R<List<Long>> ids() {
        return R.ok(userFavoriteService.getFavoriteProductIds(UserContext.getUserId()));
    }

    @Operation(summary = "分页获取收藏商品")
    @GetMapping("/list")
    public R<PageResult<Map<String, Object>>> list(@RequestParam(defaultValue = "1") Integer pageNum,
                                                    @RequestParam(defaultValue = "10") Integer pageSize) {
        return R.ok(userFavoriteService.getFavorites(UserContext.getUserId(), pageNum, pageSize));
    }
}
