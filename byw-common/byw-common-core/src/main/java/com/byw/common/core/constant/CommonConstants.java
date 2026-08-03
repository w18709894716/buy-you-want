package com.byw.common.core.constant;

public class CommonConstants {

    public static final String HEADER_USER_ID = "X-User-Id";
    public static final String HEADER_USERNAME = "X-Username";
    public static final String HEADER_USER_ROLE = "X-User-Role";
    public static final String HEADER_SHOP_ID = "X-Shop-Id";
    public static final String HEADER_USER_TYPE = "X-User-Type";

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

    public static final int STATUS_NORMAL = 1;
    public static final int STATUS_DISABLED = 0;
    public static final int STATUS_DELETED = -1;

    public static final String DATE_FORMAT = "yyyy-MM-dd HH:mm:ss";

    public static final long TOKEN_EXPIRE_MS = 24 * 60 * 60 * 1000L;

    private CommonConstants() {}
}
