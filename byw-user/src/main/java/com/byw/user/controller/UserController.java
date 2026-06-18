package com.byw.user.controller;

import com.byw.common.core.result.R;
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
    @GetMapping("/me")
    @RequireLogin
    public R<User> getCurrentUser() {
        Long userId = UserContext.getUserId();
        return R.ok(userService.getById(userId));
    }

    @Operation(summary = "根据ID获取用户")
    @GetMapping("/{userId}")
    public R<User> getUserById(@PathVariable Long userId) {
        return R.ok(userService.getById(userId));
    }

    @Operation(summary = "更新用户信息")
    @PutMapping
    @RequireLogin
    public R<Void> updateUser(@RequestBody User user) {
        user.setId(UserContext.getUserId());
        user.setPassword(null); // Prevent password update via this endpoint
        userService.updateById(user);
        return R.ok();
    }
}
