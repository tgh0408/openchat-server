package com.openchat.filter;

import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class RealIPFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(org.springframework.web.server.ServerWebExchange exchange, org.springframework.cloud.gateway.filter.GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String clientIp = request.getHeaders().getFirst("X-Forwarded-For");

        if (clientIp == null) {
            clientIp = request.getRemoteAddress() != null ? request.getRemoteAddress().getAddress().getHostAddress() : "unknown";
        }

        // 你可以在这里将clientIp以某种方式传递给后端服务，比如通过请求头
        ServerHttpRequest modifiedRequest = request.mutate()
                .header("X-Real-IP", clientIp)
                .build();

        return chain.filter(exchange.mutate().request(modifiedRequest).build());
    }

    @Override
    public int getOrder() {
        return -1; // 设定过滤器的优先级
    }
}