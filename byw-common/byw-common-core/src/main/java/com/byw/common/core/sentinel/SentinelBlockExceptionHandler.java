package com.byw.common.core.sentinel;

import com.alibaba.csp.sentinel.adapter.spring.webmvc.callback.BlockExceptionHandler;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.alibaba.csp.sentinel.slots.block.authority.AuthorityException;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeException;
import com.alibaba.csp.sentinel.slots.block.flow.FlowException;
import com.byw.common.core.exception.ResultCode;
import com.byw.common.core.result.R;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

/**
 * Sentinel 限流/降级 统一异常处理器
 * 当请求被 Sentinel 拦截时，返回统一的 JSON 响应
 */
@RequiredArgsConstructor
public class SentinelBlockExceptionHandler implements BlockExceptionHandler {

    private final ObjectMapper objectMapper;

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, BlockException e) throws Exception {
        response.setStatus(429);
        response.setContentType("application/json;charset=UTF-8");

        ResultCode resultCode;
        if (e instanceof FlowException) {
            resultCode = ResultCode.RATE_LIMIT;
        } else if (e instanceof DegradeException) {
            resultCode = ResultCode.SYSTEM_ERROR;
        } else if (e instanceof AuthorityException) {
            resultCode = ResultCode.FORBIDDEN;
        } else {
            resultCode = ResultCode.RATE_LIMIT;
        }

        R<?> result = R.fail(resultCode);
        objectMapper.writeValue(response.getWriter(), result);
    }
}
