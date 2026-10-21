package com.byw.common.security.config;

import com.byw.common.core.exception.BusinessException;
import com.byw.common.security.feign.FeignAuthRelayInterceptor;
import com.byw.common.security.feign.FeignTraceIdInterceptor;
import com.byw.common.security.sign.InternalSignature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.Request;
import feign.RequestInterceptor;
import feign.Util;
import feign.codec.ErrorDecoder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * 仅在存在 Feign 时生效：注册身份透传拦截器。
 */
@AutoConfiguration
@ConditionalOnClass(RequestInterceptor.class)
public class FeignRelayAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public FeignTraceIdInterceptor feignTraceIdInterceptor() {
        return new FeignTraceIdInterceptor();
    }

    @Bean
    @ConditionalOnMissingBean
    public FeignAuthRelayInterceptor feignAuthRelayInterceptor(
            @Value("${" + InternalSignature.PROPERTY_NAME + ":}")
            String internalSecret) {
        // 无兜底默认值：密钥缺失或仍是源码默认值时直接启动失败（见 requireUsableSecret）
        InternalSignature.requireUsableSecret(internalSecret);
        return new FeignAuthRelayInterceptor(internalSecret);
    }

    /**
     * Feign 全局超时（R9）。
     *
     * <p>默认值来自 feign 自身的 10s/60s —— 对下单这类同步链路太宽松：product/promotion 任一抖动，
     * 调用方线程会被挂住几十秒，Sentinel 熔断前就已堆积大量请求。这里收敛到 3s/5s。
     *
     * <p>刻意<b>不配置 Retryer</b>：Feign 默认 NEVER_RETRY，下游写接口（useCoupon/lockStock/
     * clearCartItems）都非幂等，超时后重试可能造成重复核销/重复预占，宁可让调用方快速失败并依赖
     * 上层补偿（见 R4）与用户重试。若某个只读接口确需重试，应在该 FeignClient 上单独指定。
     *
     * <p>以父上下文 Bean 暴露，Spring Cloud 会对所有 FeignClient 生效，无需逐服务配置。
     */
    /**
     * 把下游 4xx 的响应体（统一 R 结构）还原成 {@link BusinessException}。
     *
     * <p><b>为什么需要</b>：下游服务通过全局异常处理器把"参数校验失败 / 未登录 / 无权限"返回成
     * 400 / 401 / 403 + {@code R{code,message}}；而 Feign 对 4xx 默认抛 {@code FeignException}，
     * BFF 的异常处理器只能把它兜成 <b>500「系统内部错误」</b>——前端既看不到那句中文提示，
     * 也无法据此做 401 跳登录。
     *
     * <p>这里把响应体里的 code/message 还原成 BusinessException，让 BFF 按既有约定输出
     * （401/403 仍会被全局处理器映射成真实 HTTP 状态）。<b>5xx 不处理</b>：
     * 保持 Feign 默认行为，避免把服务端故障伪装成业务错误。
     */
    @Bean
    @ConditionalOnMissingBean
    public ErrorDecoder feignBusinessErrorDecoder(ObjectMapper objectMapper) {
        ErrorDecoder fallback = new ErrorDecoder.Default();
        return (methodKey, response) -> {
            int status = response.status();
            if (status < 400 || status >= 500) {
                return fallback.decode(methodKey, response);
            }
            try {
                String body = Util.toString(response.body().asReader(StandardCharsets.UTF_8));
                JsonNode node = objectMapper.readTree(body);
                String message = node.path("message").asText(null);
                if (message != null && !message.isBlank()) {
                    return new BusinessException(node.path("code").asInt(status), message);
                }
            } catch (Exception ignored) {
                // 响应体不是 R 结构（框架/网关直接返回的错误页）→ 保持 Feign 默认行为
            }
            return fallback.decode(methodKey, response);
        };
    }

    @Bean
    @ConditionalOnMissingBean
    public Request.Options feignRequestOptions(
            @Value("${byw.feign.connect-timeout-ms:3000}") long connectTimeoutMs,
            @Value("${byw.feign.read-timeout-ms:5000}") long readTimeoutMs) {
        return new Request.Options(connectTimeoutMs, TimeUnit.MILLISECONDS, readTimeoutMs, TimeUnit.MILLISECONDS, true);
    }
}
