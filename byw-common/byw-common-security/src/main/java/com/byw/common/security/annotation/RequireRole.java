package com.byw.common.security.annotation;

import java.lang.annotation.*;

/**
 * 角色访问控制：标注在方法或类上，仅允许 value 中列出的角色访问。
 * 角色取值见 {@code CommonConstants.ROLE_*}。
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequireRole {

    String[] value();
}
