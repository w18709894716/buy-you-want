package com.byw.common.security.feign;

import com.byw.common.core.trace.TraceIdHolder;
import feign.RequestInterceptor;
import feign.RequestTemplate;

/**
 * 把当前线程 MDC 中的 traceId 透传给下游服务，使跨服务调用的日志可以用同一个 traceId 串联。
 */
public class FeignTraceIdInterceptor implements RequestInterceptor {

    @Override
    public void apply(RequestTemplate template) {
        String traceId = TraceIdHolder.get();
        if (traceId != null && !traceId.isBlank()) {
            template.header(TraceIdHolder.HEADER, traceId);
        }
    }
}
