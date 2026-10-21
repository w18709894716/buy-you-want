package com.byw.common.security.context;

import com.byw.common.core.constant.CommonConstants;

public class UserContext {

    private static final ThreadLocal<Long> USER_ID = new ThreadLocal<>();
    private static final ThreadLocal<String> USERNAME = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> IS_ADMIN = new ThreadLocal<>();
    private static final ThreadLocal<String> ROLE = new ThreadLocal<>();
    private static final ThreadLocal<Long> SHOP_ID = new ThreadLocal<>();
    private static final ThreadLocal<String> USER_TYPE = new ThreadLocal<>();

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

    public static void setUserType(String userType) {
        USER_TYPE.set(userType);
    }

    /**
     * 当前登录主体类型：c 会员 / sys 平台员工 / merchant 商家账号。
     */
    public static String getUserType() {
        return USER_TYPE.get();
    }

    /**
     * 是否具备操作指定店铺资源的资格（fail-closed）。
     *
     * <p>规则：商家账号必须与资源同店；平台管理员放行；**既无店铺上下文又非管理员一律拒绝**。
     * 早期实现写成"shopId 为空则跳过校验"，导致不带身份头的请求（含直连服务端口、
     * 或经内部接口伪造身份失败被降级为游客的场景）可以直接操作任意店铺资源。
     */
    public static boolean canAccessShop(Long resourceShopId) {
        Long shopId = SHOP_ID.get();
        // shopId <= 0 视为"没有店铺上下文"（0 是早期 refresh 写入的历史哨兵值），与 null 等价处理
        if (shopId != null && shopId > 0) {
            return shopId.equals(resourceShopId);
        }
        // Boolean.TRUE.equals：IS_ADMIN 未设置时是 null，直接拆箱会 NPE（变成 500 而不是干净的拒绝）
        return Boolean.TRUE.equals(isAdmin());
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
        USER_TYPE.remove();
    }
}
