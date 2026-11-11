package com.openchat.filter;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.factory.PreserveHostHeaderGatewayFilterFactory;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR;

/**
 * MinIO 签名包含原始 Host（含非默认端口），不能替换为代理上游的内网地址。
 * 只作用于 Nacos 中 id=minio 的路由，不改变业务服务、OpenChat和 WebSocket 的转发行为。
 */
@Component
public class MinioHostHeaderFilter implements GlobalFilter, Ordered {

    private final GatewayFilter preserveHostHeader;

    public MinioHostHeaderFilter(PreserveHostHeaderGatewayFilterFactory factory) {
        this.preserveHostHeader = factory.apply();
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        Route route = exchange.getAttribute(GATEWAY_ROUTE_ATTR);
        if (route != null && "minio".equals(route.getId())) {
            return preserveHostHeader.filter(exchange, chain);
        }
        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        return 0;
    }
}
