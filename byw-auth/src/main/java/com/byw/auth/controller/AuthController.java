package com.byw.auth.controller;

import com.byw.auth.dto.LoginRequest;
import com.byw.auth.dto.LoginResponse;
import com.byw.auth.dto.RegisterRequest;
import com.byw.auth.service.AuthService;
import com.byw.auth.support.AuthCookieSupport;
import com.byw.common.core.constant.CommonConstants;
import jakarta.servlet.http.HttpServletResponse;
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
    private final AuthCookieSupport authCookieSupport;

    /** 双模式取 token：Authorization 头优先，其次 HttpOnly Cookie */
    private String resolveToken(String authorization, String cookieToken) {
        if (authorization != null && !authorization.isBlank()) {
            String value = authorization.trim();
            return value.startsWith("Bearer ") ? value.substring(7) : value;
        }
        return cookieToken == null || cookieToken.isBlank() ? null : cookieToken;
    }

    /**
     * 按端的 Cookie 名取值：该端有就用它；没有则退回其它端（兼容尚未带 X-Client-App 头的旧前端）。
     * 注意这是"回退"而不是"混用"：三端同名 Cookie 会互相覆盖，所以主力必须是按端名。
     */
    private String pickCookie(String clientApp, String adminCookie, String merchantCookie, String userCookie) {
        java.util.Map<String, String> byName = new java.util.HashMap<>();
        byName.put(CommonConstants.AUTH_COOKIE_ADMIN, adminCookie);
        byName.put(CommonConstants.AUTH_COOKIE_MERCHANT, merchantCookie);
        byName.put(CommonConstants.AUTH_COOKIE_NAME, userCookie);
        String preferred = byName.get(AuthCookieSupport.cookieNameOf(clientApp));
        if (preferred != null && !preferred.isBlank()) {
            return preferred;
        }
        return byName.values().stream()
                .filter(v -> v != null && !v.isBlank())
                .findFirst()
                .orElse(null);
    }

    /** 需要 token 的端点：取不到就 401，而不是把 null 传进 service */
    private String requireToken(String authorization, String cookieToken) {
        String token = resolveToken(authorization, cookieToken);
        if (token == null) {
            throw new BusinessException(com.byw.common.core.exception.ResultCode.UNAUTHORIZED);
        }
        return token;
    }

    @Operation(summary = "密码登录")
    @SentinelResource(value = "auth:login", fallback = "loginFallback", exceptionsToIgnore = BusinessException.class)
    @PostMapping("/login")
    public R<LoginResponse> login(@Valid @RequestBody LoginRequest request, HttpServletResponse response) {
        LoginResponse result = authService.login(request);
        // C 端（Nuxt）仍是"Authorization 头"模式，**不下发 Cookie**：
        // 它无法删除 HttpOnly Cookie（其 clearLegacyCookie 是空操作），残留的 byw_token 只会造成
        // "头里的新 token 和 Cookie 里的旧 token 争夺身份"，表现为刚登录就被判失效、自动退出。
        return R.ok(result);
    }

    @Operation(summary = "商家登录")
    @SentinelResource(value = "auth:merchantLogin", fallback = "loginFallback", exceptionsToIgnore = BusinessException.class)
    @PostMapping("/merchant/login")
    public R<LoginResponse> merchantLogin(@Valid @RequestBody LoginRequest request, HttpServletResponse response) {
        LoginResponse result = authService.merchantLogin(request);
        authCookieSupport.write(response, result == null ? null : result.getToken(), CommonConstants.AUTH_COOKIE_MERCHANT);
        return R.ok(result);
    }

    @Operation(summary = "平台管理员登录")
    @SentinelResource(value = "auth:adminLogin", fallback = "loginFallback", exceptionsToIgnore = BusinessException.class)
    @PostMapping("/admin/login")
    public R<LoginResponse> adminLogin(@Valid @RequestBody LoginRequest request, HttpServletResponse response) {
        LoginResponse result = authService.adminLogin(request);
        authCookieSupport.write(response, result == null ? null : result.getToken(), CommonConstants.AUTH_COOKIE_ADMIN);
        return R.ok(result);
    }

    @Operation(summary = "用户注册")
    @PostMapping("/register")
    public R<Void> register(@Valid @RequestBody RegisterRequest request) {
        authService.register(request);
        return R.ok();
    }

    @Operation(summary = "刷新Token")
    @PostMapping("/refresh")
    public R<LoginResponse> refreshToken(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = CommonConstants.HEADER_CLIENT_APP, required = false) String clientApp,
            @CookieValue(value = CommonConstants.AUTH_COOKIE_ADMIN, required = false) String adminCookie,
            @CookieValue(value = CommonConstants.AUTH_COOKIE_MERCHANT, required = false) String merchantCookie,
            @CookieValue(value = CommonConstants.AUTH_COOKIE_NAME, required = false) String userCookie,
            HttpServletResponse response) {
        String cookieName = AuthCookieSupport.cookieNameOf(clientApp);
        LoginResponse result = authService.refreshToken(
                requireToken(authorization, pickCookie(clientApp, adminCookie, merchantCookie, userCookie)));
        // 旧 token 已失效，Cookie 必须换成新的，否则下次续期会拿旧 token 去换。
        // 头模式的客户端（不带 X-Client-App）不写 Cookie，避免给它种下自己删不掉的 HttpOnly Cookie。
        if (clientApp != null && !clientApp.isBlank()) {
            authCookieSupport.write(response, result == null ? null : result.getToken(), cookieName);
        }
        return R.ok(result);
    }

    @Operation(summary = "当前 Token 剩余有效秒数（前端据此在过期前主动续期）")
    @GetMapping("/token-ttl")
    public R<Long> tokenTtl(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = CommonConstants.HEADER_CLIENT_APP, required = false) String clientApp,
            @CookieValue(value = CommonConstants.AUTH_COOKIE_ADMIN, required = false) String adminCookie,
            @CookieValue(value = CommonConstants.AUTH_COOKIE_MERCHANT, required = false) String merchantCookie,
            @CookieValue(value = CommonConstants.AUTH_COOKIE_NAME, required = false) String userCookie) {
        return R.ok(authService.getTokenTtl(
                resolveToken(authorization, pickCookie(clientApp, adminCookie, merchantCookie, userCookie))));
    }

    @Operation(summary = "退出登录")
    @PostMapping("/logout")
    public R<Void> logout(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = CommonConstants.HEADER_CLIENT_APP, required = false) String clientApp,
            @CookieValue(value = CommonConstants.AUTH_COOKIE_ADMIN, required = false) String adminCookie,
            @CookieValue(value = CommonConstants.AUTH_COOKIE_MERCHANT, required = false) String merchantCookie,
            @CookieValue(value = CommonConstants.AUTH_COOKIE_NAME, required = false) String userCookie,
            HttpServletResponse response) {
        authService.logout(requireToken(authorization, pickCookie(clientApp, adminCookie, merchantCookie, userCookie)));
        if (clientApp != null && !clientApp.isBlank()) {
            authCookieSupport.clear(response, AuthCookieSupport.cookieNameOf(clientApp));
        }
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
