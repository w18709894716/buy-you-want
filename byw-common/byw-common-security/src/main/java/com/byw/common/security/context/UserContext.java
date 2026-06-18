package com.byw.common.security.context;

public class UserContext {

    private static final ThreadLocal<Long> USER_ID = new ThreadLocal<>();
    private static final ThreadLocal<String> USERNAME = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> IS_ADMIN = new ThreadLocal<>();

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

    public static void clear() {
        USER_ID.remove();
        USERNAME.remove();
        IS_ADMIN.remove();
    }
}
