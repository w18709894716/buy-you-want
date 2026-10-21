package com.byw.common.security.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 内部服务间接口标记。
 *
 * <p>与 {@link Public} 的区别：{@code @Public} 是"对所有人开放"，{@code @InternalApi} 是
 * "仅对持有服务间密钥的调用方开放"。标注本注解的接口（各服务的 {@code *FeignImpl}）
 * 必须携带合法的 {@code X-Internal-Signature}，否则一律 403，且不参与用户登录态校验
 * （库存回补、结算等后台链路的 Feign 调用没有用户上下文）。
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface InternalApi {
}
