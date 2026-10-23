package com.byw.gateway.config;

import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

import java.util.Optional;

/**
 * 网关限流键解析。
 *
 * <p>这里有**两个** {@code KeyResolver}，靠 {@code @Primary} 解决注入歧义：
 * <ul>
 *   <li>{@code globalIpKeyResolver}（{@code @Primary}）：全局 {@code default-filters} 用它，
 *       以及 {@code RequestRateLimiterGatewayFilterFactory} 按类型注入时的默认值；</li>
 *   <li>{@code pathIpKeyResolver}：敏感接口在路由上通过 SpEL {@code key-resolver: "#{@pathIpKeyResolver}"}
 *       显式指定，走独立的桶。</li>
 * </ul>
 * key 语义 = "客户端 IP + 路由 id"，即**按接口分组限流**：
 * <ul>
 *   <li>全局桶（50/100）对每个接口各自生效 → 刷一个接口不会拖垮同出口的其它接口；</li>
 *   <li>登录/短信/下单/支付在路由上另挂更严的令牌桶，与全局桶叠加，取更严者。</li>
 * </ul>
 * 两个 bean 的 key 必须带不同后缀（{@code :global} / {@code :strict}），否则同一个请求
 * 会被两道过滤器各扣一次令牌，等效强度翻倍。
 *
 * <p>关于 {@code X-Forwarded-For}：默认**不信任**。直连部署时该请求头由客户端完全可控，
 * 若用它做限流键，攻击者每次换一个伪造 IP 就能绕过限流；只有在网关前面确实有可信代理
 * （Nginx/SLB）时，才把 {@code byw.gateway.rate-limit.trust-forwarded-for} 设为 {@code true}。
 */
@Configuration
public class RateLimiterConfig {

    private static final String UNKNOWN_KEY = "unknown";

    @Value("${byw.gateway.rate-limit.trust-forwarded-for:false}")
    private boolean trustForwardedFor;


    /**
     * 按"客户端 IP + 路由"限流：同一 IP 下不同接口互不占用额度。
     *
     * <p>为什么需要它：只用 IP 做 key 时，同一出口（公司/校园 NAT、4G 共享）下的所有接口共享一个桶，
     * 攻击者刷任意一个读接口就能把同出口的正常用户一起限死（邻居攻击）。改成按路由分桶后，
     * 敏感接口还能在路由上挂更严的令牌桶，两者互不影响。
     */
    @Bean
    @Primary
    public KeyResolver globalIpKeyResolver() {
        // 全局兜底桶：每个接口各自 50/s（key 后缀 :global，与下面敏感接口的桶分开）
        return exchange -> Mono.just(resolveIp(exchange) + ":" + resolveRouteKey(exchange) + ":global");
    }

    @Bean
    public KeyResolver pathIpKeyResolver() {
        // 敏感接口的精细桶：后缀 :strict。**必须与全局桶用不同 key**，
        // 否则同一个请求会被 default-filters 和路由级过滤器各扣一次令牌，等效强度翻倍（实测过）。
        return exchange -> Mono.just(resolveIp(exchange) + ":" + resolveRouteKey(exchange) + ":strict");
    }

    private String resolveIp(ServerWebExchange exchange) {
        if (trustForwardedFor) {
            String forwarded = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                return forwarded.split(",")[0].trim();
            }
        }
        return Optional.ofNullable(exchange.getRequest().getRemoteAddress())
                .map(address -> address.getAddress().getHostAddress())
                .orElse(UNKNOWN_KEY);
    }

    /**
     * 优先用路由 id 作分桶维度（等价于"按接口分组"），拿不到时退化为路径前 3 段，
     * 避免 /api/product/123 与 /api/product/456 各占一个桶把桶数量打爆。
     */
    private String resolveRouteKey(ServerWebExchange exchange) {
        Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
        if (route != null && route.getId() != null && !route.getId().isBlank()) {
            return route.getId();
        }
        String[] segments = exchange.getRequest().getPath().value().split("/");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(4, segments.length); i++) {
            if (!segments[i].isEmpty()) {
                sb.append('/').append(segments[i]);
            }
        }
        return sb.length() == 0 ? "root" : sb.toString();
    }
}
