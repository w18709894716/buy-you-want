package com.byw.common.core.trace;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 为每个 HTTP 请求建立 traceId 上下文：优先复用上游传入的 {@code X-Trace-Id}，
 * 否则生成一个，写入 MDC（日志）并回写响应头（便于前端/网关排查）。
 */
public class TraceIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String incoming = request.getHeader(TraceIdHolder.HEADER);
        String traceId = TraceIdHolder.isValid(incoming) ? incoming : TraceIdHolder.generate();
        TraceIdHolder.set(traceId);
        response.setHeader(TraceIdHolder.HEADER, traceId);
        try {
            chain.doFilter(request, response);
        } finally {
            TraceIdHolder.clear();
        }
    }
}
