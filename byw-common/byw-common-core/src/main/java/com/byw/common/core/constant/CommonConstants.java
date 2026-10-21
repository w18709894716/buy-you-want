package com.byw.common.core.constant;

public class CommonConstants {

    public static final String HEADER_USER_ID = "X-User-Id";
    public static final String HEADER_USERNAME = "X-Username";
    public static final String HEADER_USER_ROLE = "X-User-Role";
    public static final String HEADER_SHOP_ID = "X-Shop-Id";
    public static final String HEADER_USER_TYPE = "X-User-Type";
    // 身份头签名：网关注入身份头时写入，服务端凭它认定身份头来自网关而非直连伪造
    public static final String HEADER_IDENTITY_SIGNATURE = "X-Identity-Signature";

    /**
     * 承载 token 的 HttpOnly Cookie 名（与 Authorization 头**双模式**并存）。
     * 写在 Cookie 里的好处是前端 JS 读不到，降低 XSS 直接窃取 token 的风险。
     *
     * <p><b>必须按端分开命名</b>：三个前端通常部署在同一个 host（本地都是 localhost，只是端口不同），
     * 而 Cookie 不区分端口 —— 用同一个名字会互相覆盖，导致"先登 C 端、再开管理端就被 403"。
     */
    public static final String AUTH_COOKIE_ADMIN = "byw_token_admin";
    public static final String AUTH_COOKIE_MERCHANT = "byw_token_merchant";
    /** C 端（Nuxt）用；名字保持历史值，避免存量会话失效 */
    public static final String AUTH_COOKIE_NAME = "byw_token";

    /**
     * 前端显式声明"我是哪一端"，用于在同一个浏览器里同时登录三端时选对 Cookie。
     * 取值：{@code admin} / {@code merchant} / {@code web}（C 端可省略）。
     */
    public static final String HEADER_CLIENT_APP = "X-Client-App";
    public static final String CLIENT_APP_ADMIN = "admin";
    public static final String CLIENT_APP_MERCHANT = "merchant";
    public static final String CLIENT_APP_WEB = "web";
    // 服务间调用凭证：Feign 调用时写入，服务端凭它认定请求来自内部服务（与用户身份无关）
    public static final String HEADER_INTERNAL_SIGNATURE = "X-Internal-Signature";

    // ========== RBAC 角色 ==========
    public static final String ROLE_USER = "user";
    public static final String ROLE_PLATFORM_ADMIN = "platform_admin";
    public static final String ROLE_MERCHANT_OWNER = "merchant_owner";
    public static final String ROLE_MERCHANT_STAFF = "merchant_staff";

    // ========== RBAC 用户主体类型 ==========
    public static final String USER_TYPE_C = "c";          // C 端会员
    public static final String USER_TYPE_SYS = "sys";      // 平台员工
    public static final String USER_TYPE_MERCHANT = "merchant"; // 商家账号

    // 权限通配：拥有全部权限
    public static final String PERM_ALL = "*";
    // 登录后聚合权限标识写入 Redis 的 key 前缀：auth:perms:{userType}:{userId}
    public static final String AUTH_PERMS_KEY_PREFIX = "auth:perms:";

    // ========== Sa-Token 账号 Session 属性 key（byw-auth 写入，byw-gateway / byw-im 读取） ==========
    public static final String SESSION_USERNAME = "username";
    public static final String SESSION_ROLE = "role";
    public static final String SESSION_SHOP_ID = "shopId";
    public static final String SESSION_USER_TYPE = "userType";

    public static final int STATUS_NORMAL = 1;
    public static final int STATUS_DISABLED = 0;
    public static final int STATUS_DELETED = -1;

    public static final String DATE_FORMAT = "yyyy-MM-dd HH:mm:ss";

    private CommonConstants() {}
}
