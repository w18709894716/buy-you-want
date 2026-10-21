package com.byw.auth.support;

import com.byw.common.core.constant.CommonConstants;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

/**
 * 把 token 写进 HttpOnly Cookie（与 Authorization 头**双模式并存**）。
 *
 * <p><b>为什么加 Cookie</b>：token 存在 localStorage 时，任何 XSS 都能直接读走；
 * 写进 HttpOnly Cookie 后前端 JS 读不到，攻击面从"直接窃取"降为"借用页面发请求"（还可叠加 CSRF 防护）。
 *
 * <p><b>为什么是双模式</b>：存量前端仍用 {@code Authorization: Bearer} 头，网关/auth 同时接受两种方式，
 * 这样可以先上后端、再把前端切到 Cookie，任何一步出问题都能单独回退，不用三端一起改。
 *
 * <p>属性说明：{@code HttpOnly} 使 JS 不可读；{@code SameSite=Lax} 让跨站 POST 不带 Cookie（挡大部分 CSRF）；
 * {@code Secure} 仅 HTTPS 下发 —— 本地 http 调试必须 false，生产必须 true（环境变量控制）。
 */
@Slf4j
@Component
public class AuthCookieSupport {

    private static final String PATH = "; Path=/";
    private static final String HTTP_ONLY = "; HttpOnly";

    @Value("${byw.auth.cookie.secure:false}")
    private boolean secure;

    @Value("${byw.auth.cookie.same-site:Lax}")
    private String sameSite;

    @Value("${byw.auth.cookie.max-age:86400}")
    private long maxAge;

    /** 登录/续期成功后下发（覆盖旧值）；{@code cookieName} 决定写哪一端的 Cookie */
    public void write(HttpServletResponse response, String token, String cookieName) {
        if (token == null || token.isBlank()) {
            return;
        }
        response.addHeader(HttpHeaders.SET_COOKIE, build(cookieName, token, maxAge));
    }

    /** 退出登录：立即过期 */
    public void clear(HttpServletResponse response, String cookieName) {
        response.addHeader(HttpHeaders.SET_COOKIE, build(cookieName, "", 0));
    }

    /**
     * 按 {@code X-Client-App} 头选 Cookie 名；缺省（如 Nuxt 未传）按 C 端处理。
     */
    public static String cookieNameOf(String clientApp) {
        if (clientApp == null) {
            return CommonConstants.AUTH_COOKIE_NAME;
        }
        return switch (clientApp.trim().toLowerCase()) {
            case CommonConstants.CLIENT_APP_ADMIN -> CommonConstants.AUTH_COOKIE_ADMIN;
            case CommonConstants.CLIENT_APP_MERCHANT -> CommonConstants.AUTH_COOKIE_MERCHANT;
            default -> CommonConstants.AUTH_COOKIE_NAME;
        };
    }

    private String build(String cookieName, String value, long maxAgeSeconds) {
        StringBuilder sb = new StringBuilder();
        sb.append(cookieName).append('=').append(value)
                .append(PATH).append(HTTP_ONLY)
                .append("; SameSite=").append(sameSite)
                .append("; Max-Age=").append(maxAgeSeconds);
        if (secure) {
            sb.append("; Secure");
        }
        return sb.toString();
    }
}
