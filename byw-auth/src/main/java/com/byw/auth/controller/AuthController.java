package com.byw.auth.controller;

import com.byw.auth.dto.LoginRequest;
import com.byw.auth.dto.LoginResponse;
import com.byw.auth.dto.RegisterRequest;
import com.byw.auth.service.AuthService;
import com.byw.common.core.result.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "认证中心", description = "登录/注册/Token刷新")
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "密码登录")
    @PostMapping("/login")
    public R<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return R.ok(authService.login(request));
    }

    @Operation(summary = "用户注册")
    @PostMapping("/register")
    public R<Void> register(@Valid @RequestBody RegisterRequest request) {
        authService.register(request);
        return R.ok();
    }

    @Operation(summary = "刷新Token")
    @PostMapping("/refresh")
    public R<LoginResponse> refreshToken(@RequestHeader("Authorization") String token) {
        return R.ok(authService.refreshToken(token));
    }

    @Operation(summary = "退出登录")
    @PostMapping("/logout")
    public R<Void> logout(@RequestHeader("Authorization") String token) {
        authService.logout(token);
        return R.ok();
    }

    @Operation(summary = "发送短信验证码")
    @PostMapping("/sms/send")
    public R<Void> sendSmsCode(@RequestParam String phone) {
        authService.sendSmsCode(phone);
        return R.ok();
    }
}
