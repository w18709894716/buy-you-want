package com.byw.common.security.annotation;

import java.lang.annotation.*;

/**
 * 权限标识访问控制：标注在方法或类上，要求当前登录主体拥有指定权限标识。
 * 权限标识格式 {@code 模块:操作}，如 product:audit、m:order:ship。
 * 权限集合登录时聚合写入 Redis，拥有 {@code *} 通配即视为全权限。
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequirePerm {

    String value();
}
