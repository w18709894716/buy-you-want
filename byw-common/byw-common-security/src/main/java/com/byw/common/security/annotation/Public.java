package com.byw.common.security.annotation;

import java.lang.annotation.*;

/**
 * 标注免登录访问的接口或控制器。
 * 服务层默认关闭（非 @Public 接口一律需登录），公开接口须显式声明本注解。
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Public {
}
