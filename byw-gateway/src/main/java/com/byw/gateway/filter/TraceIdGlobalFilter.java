package com.byw.gateway.filter;

import com.byw.common.core.trace.TraceIdHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 网关侧 traceId：复用上游传入的 {@code X-Trace-Id}（校验合法后），否则生成一个，
 * 写进转发给下游的请求头与返回给调用方的响应头。
 *
 * <p>为什么必须在网关补：{@code TraceIdAutoConfiguration} 只在 Servlet 应用生效，网关是 WebFlux 被有意跳过，
 * 于是每个下游服务各自生成一个 traceId，网关自己的日志里 {@code %X{traceId}} 恒为空 ——
 * 一次跨服务调用没有任何一个 ID 能把网关和下游的日志串起来。下游的 {@code TraceIdFilter}
 * 本来就优先复用上游传入的头，网关补上之后整条链路自动共用同一个 traceId。
 *
 * <p>MDC 只在过滤器链的**同步段**内有效：Reactor 后续的异步回调可能已切换到别的事件循环线程，
 * 拿不到这个值。这里用"进入时保存、退出时还原"而不是无脑 clear，避免把同线程上一个请求
 * 尚未结束的上下文误删（ThreadLocal 串号的老问题）。异步段的日志如需 traceId，从 exchange 属性里取。
 */
@Slf4j
@Component
public class TraceIdGlobalFilter implements GlobalFilter, Ordered {

    /** exchange 属性键：异步回调里仍可取到本次请求的 traceId */
    public static final String ATTR_TRACE_ID = "byw.traceId";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String incoming = exchange.getRequest().getHeaders().getFirst(TraceIdHolder.HEADER);
        String traceId = TraceIdHolder.isValid(incoming) ? incoming : TraceIdHolder.generate();
        exchange.getAttributes().put(ATTR_TRACE_ID, traceId);
        // 回写响应头，前端报错时能直接把 traceId 提供给后端查日志
        exchange.getResponse().getHeaders().set(TraceIdHolder.HEADER, traceId);

        ServerHttpRequest request = exchange.getRequest().mutate()
                .headers(headers -> headers.set(TraceIdHolder.HEADER, traceId))
                .build();

        String previous = TraceIdHolder.get();
        TraceIdHolder.set(traceId);
        try {
            return chain.filter(exchange.mutate().request(request).build());
        } finally {
            if (previous == null) {
                TraceIdHolder.clear();
            } else {
                TraceIdHolder.set(previous);
            }
        }
    }

    @Override
    public int getOrder() {
        // 早于 ApiDocsAccessFilter(-120) 与 AuthGlobalFilter(-100)，让它们打的日志也带上 traceId
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
