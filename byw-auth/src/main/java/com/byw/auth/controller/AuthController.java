package com.byw.auth.controller;

import com.byw.auth.dto.LoginRequest;
import com.byw.auth.dto.LoginResponse;
import com.byw.auth.dto.RegisterRequest;
import com.byw.auth.service.AuthService;
import com.byw.common.core.exception.BusinessException;
import com.byw.common.core.result.R;
import com.byw.common.security.annotation.Public;
import com.alibaba.csp.sentinel.annotation.SentinelResource;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Slf4j
@Tag(name = "认证中心", description = "登录/注册/Token刷新")
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Public
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "密码登录")
    @SentinelResource(value = "auth:login", fallback = "loginFallback", exceptionsToIgnore = BusinessException.class)
    @PostMapping("/login")
    public R<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return R.ok(authService.login(request));
    }

    @Operation(summary = "商家登录")
    @SentinelResource(value = "auth:merchantLogin", fallback = "loginFallback", exceptionsToIgnore = BusinessException.class)
    @PostMapping("/merchant/login")
    public R<LoginResponse> merchantLogin(@Valid @RequestBody LoginRequest request) {
        return R.ok(authService.merchantLogin(request));
    }

    @Operation(summary = "平台管理员登录")
    @SentinelResource(value = "auth:adminLogin", fallback = "loginFallback", exceptionsToIgnore = BusinessException.class)
    @PostMapping("/admin/login")
    public R<LoginResponse> adminLogin(@Valid @RequestBody LoginRequest request) {
        return R.ok(authService.adminLogin(request));
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

    // ========== Sentinel fallback ==========
    // 业务异常已通过 exceptionsToIgnore 放行给全局异常处理器返回真实提示，
    // 此处仅兜底真正的限流/熔断/系统异常，并记录日志便于排查。
    private R<LoginResponse> loginFallback(LoginRequest request, Throwable ex) {
        log.warn("登录被限流/熔断或发生系统异常: username={}, cause={}",
                request != null ? request.getUsername() : null, ex.toString());
        return R.fail("系统繁忙，请稍后再试");
    }
}
