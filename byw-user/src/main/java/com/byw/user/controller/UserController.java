package com.byw.user.controller;

import jakarta.validation.Valid;
import com.alicp.jetcache.anno.CacheInvalidate;
import com.byw.common.core.constant.CacheNames;
import com.byw.common.core.result.R;
import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.byw.common.security.annotation.RequireLogin;
import com.byw.common.security.context.UserContext;
import com.byw.user.entity.User;
import com.byw.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "用户中心", description = "用户信息管理")
@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @Operation(summary = "获取当前用户信息")
    @SentinelResource(value = "user:info", fallback = "currentUserFallback")
    @GetMapping("/me")
    @RequireLogin
    public R<User> getCurrentUser() {
        Long userId = UserContext.getUserId();
        User user = userService.getUserDetail(userId);
        if (user != null) {
            // 密码哈希属于凭据，任何出网接口都不得返回（见 docs/development/security-hardening.md 第 3 条）。
            // 手机号/邮箱是本人信息、个人中心需要展示，与公开端点 /user/{userId} 不同，这里保留。
            // getUserDetail 走 JetCache remote（无本地缓存），每次都是反序列化出的新实例，置空不会污染缓存。
            user.setPassword(null);
        }
        return R.ok(user);
    }

    @Operation(summary = "根据ID获取用户")
    @GetMapping("/{userId}")
    public R<User> getUserById(@PathVariable Long userId) {
        User user = userService.getUserDetail(userId);
        if (user != null) {
            // 公开端点：裁剪敏感字段，仅保留展示所需的公开信息
            user.setPassword(null);
            user.setPhone(null);
            user.setEmail(null);
        }
        return R.ok(user);
    }

    @Operation(summary = "更新用户信息")
    @PutMapping
    @RequireLogin
    // JetCache 在方法体执行完才解析 key，此时 user.id 已被方法体赋值
    @CacheInvalidate(name = CacheNames.USER_INFO, key = "#user.id")
    public R<Void> updateUser(@Valid @RequestBody User user) {
        user.setId(UserContext.getUserId());
        user.setPassword(null); // Prevent password update via this endpoint
        userService.updateById(user);
        return R.ok();
    }

    // ========== Sentinel fallback ==========
    private R<User> currentUserFallback(Throwable ex) {
        return R.fail("系统繁忙，请稍后再试");
    }
}
