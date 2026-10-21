package com.byw.common.core.util;

/**
 * Sa-Token 登录ID 命名空间工具。
 *
 * <p>C 端会员（t_user）、平台员工（t_sys_user）、商家账号（t_merchant_account）三张表的主键
 * 各自从 1 开始自增，数字 ID 空间完全重叠。若直接以主键作为 Sa-Token 的 loginId，
 * 三端会话会落在同一个 {@code satoken:login:session:{id}} 键上：后登录者覆盖前者，
 * 而先登录者的旧 token 仍然有效（is-concurrent=true / is-share=false），
 * 于是可用 C 端 token 读到平台管理员会话里的角色，造成越权。
 *
 * <p>因此 loginId 统一为 {@code userType:userId}（如 {@code c:1}、{@code sys:1}、{@code merchant:1}），
 * 三端互不干扰。同时兼容历史无前缀的纯数字 loginId（用户属性仍从 SaSession 读取）。
 */
public final class LoginIds {

    private static final String SEPARATOR = ":";

    /** 组装命名空间化的 loginId */
    public static String of(String userType, Long userId) {
        return userType + SEPARATOR + userId;
    }

    /**
     * 从 loginId 解析用户ID：既支持 {@code c:1} 也兼容历史 {@code 1}；无法解析返回 null。
     */
    public static Long userId(Object loginId) {
        if (loginId == null) {
            return null;
        }
        String value = String.valueOf(loginId);
        int idx = value.lastIndexOf(SEPARATOR);
        String idPart = idx >= 0 ? value.substring(idx + 1) : value;
        if (idPart.isEmpty()) {
            return null;
        }
        try {
            return Long.valueOf(idPart);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 从 loginId 解析账号类型（c/sys/merchant）；无前缀的历史 loginId 返回 null。
     */
    public static String userType(Object loginId) {
        if (loginId == null) {
            return null;
        }
        String value = String.valueOf(loginId);
        int idx = value.indexOf(SEPARATOR);
        return idx > 0 ? value.substring(0, idx) : null;
    }

    private LoginIds() {
    }
}
