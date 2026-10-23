package com.byw.gateway.filter;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * API 文档访问开关。
 *
 * <p>网关此前无条件把 {@code /v3/api-docs/byw-*} 与 {@code /swagger-ui} 暴露出去，
 * 任何人都能拿到全部服务的接口签名、参数与错误码结构（信息泄露，且为攻击者省去探测成本）。
 *
 * <p>默认开启（本地开发/联调需要），但会打印 warn；生产通过
 * {@code BYW_API_DOCS_ENABLED=false} 关闭后直接返回 404。
 */
@Slf4j
@Component
public class ApiDocsAccessFilter implements GlobalFilter, Ordered {

    /**
     * 关闭文档后要一并挡住的路径前缀。
     *
     * <p>必须与 {@code byw-common-security} 的 {@code WebMvcConfig} 里各服务免登录放行的路径保持同一份口径：
     * 那边把 {@code /doc.html}、{@code /swagger-resources} 也从鉴权拦截器里排除了，
     * 这里若只挡 {@code /v3/api-docs} 和 {@code /swagger-ui}，等于"关了文档但 knife4j 首页还开着"。
     */
    private static final List<String> DOC_PATH_PREFIXES = List.of(
            "/v3/api-docs", "/swagger-ui", "/swagger-resources", "/doc.html", "/webjars");

    @Value("${byw.gateway.api-docs.enabled:true}")
    private boolean apiDocsEnabled;

    @PostConstruct
    public void warnIfEnabled() {
        if (apiDocsEnabled) {
            log.warn("API 文档（/v3/api-docs、/swagger-ui）当前对外可访问，会暴露全部接口与参数结构；"
                    + "生产环境请设置 BYW_API_DOCS_ENABLED=false");
        }
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!apiDocsEnabled) {
            String path = exchange.getRequest().getURI().getPath();
            if (DOC_PATH_PREFIXES.stream().anyMatch(path::startsWith)) {
                exchange.getResponse().setStatusCode(HttpStatus.NOT_FOUND);
                return exchange.getResponse().setComplete();
            }
        }
        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        // 早于 AuthGlobalFilter(-100) 执行，关闭时不必再走鉴权链路
        return -120;
    }
}
