package com.byw.common.security.context;

import com.byw.common.core.constant.CommonConstants;

public class UserContext {

    private static final ThreadLocal<Long> USER_ID = new ThreadLocal<>();
    private static final ThreadLocal<String> USERNAME = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> IS_ADMIN = new ThreadLocal<>();
    private static final ThreadLocal<String> ROLE = new ThreadLocal<>();
    private static final ThreadLocal<Long> SHOP_ID = new ThreadLocal<>();

    public static void setUserId(Long userId) {
        USER_ID.set(userId);
    }

    public static Long getUserId() {
        return USER_ID.get();
    }

    public static void setUsername(String username) {
        USERNAME.set(username);
    }

    public static String getUsername() {
        return USERNAME.get();
    }

    public static void setIsAdmin(Boolean isAdmin) {
        IS_ADMIN.set(isAdmin);
    }

    public static Boolean isAdmin() {
        return Boolean.TRUE.equals(IS_ADMIN.get());
    }

    public static void setRole(String role) {
        ROLE.set(role);
    }

    public static String getRole() {
        return ROLE.get();
    }

    public static void setShopId(Long shopId) {
        SHOP_ID.set(shopId);
    }

    /**
     * 当前登录主体所属店铺ID：商家账号有值，平台管理员/普通用户为 null。
     * 商家侧数据查询应以此做租户范围过滤。
     */
    public static Long getShopId() {
        return SHOP_ID.get();
    }

    /**
     * 是否商家角色（店主或员工）。
     */
    public static boolean isMerchant() {
        String role = ROLE.get();
        return CommonConstants.ROLE_MERCHANT_OWNER.equals(role)
                || CommonConstants.ROLE_MERCHANT_STAFF.equals(role);
    }

    /**
     * 是否命中给定角色之一。
     */
    public static boolean hasAnyRole(String... roles) {
        String current = ROLE.get();
        if (current == null || roles == null) {
            return false;
        }
        for (String r : roles) {
            if (current.equals(r)) {
                return true;
            }
        }
        return false;
    }

    public static void clear() {
        USER_ID.remove();
        USERNAME.remove();
        IS_ADMIN.remove();
        ROLE.remove();
        SHOP_ID.remove();
    }
}
