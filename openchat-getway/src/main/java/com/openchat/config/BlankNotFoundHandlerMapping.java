package com.openchat.config;

import lombok.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.config.GlobalCorsProperties;
import org.springframework.cloud.gateway.handler.FilteringWebHandler;
import org.springframework.cloud.gateway.handler.RoutePredicateHandlerMapping;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebHandler;
import reactor.core.publisher.Mono;

/**
 * 路由未命中时返回空白 404 页。
 * <p>
 * 网关默认在匹配不到路由时交由 Spring Boot 的通用错误处理，返回一段 JSON 错误体。
 * 这里接管"没有路由匹配"这个分支，直接写出状态码 404、无响应体、不设置 Content-Type，
 * 浏览器侧就是一张空白页。
 * <p>
 * 只影响"路径不在路由规则里"的情况；下游服务自己返回的 404 不受影响。
 */
@Primary
@Component
public class BlankNotFoundHandlerMapping extends RoutePredicateHandlerMapping {

    private static final Logger log = LoggerFactory.getLogger(BlankNotFoundHandlerMapping.class);

    public BlankNotFoundHandlerMapping(FilteringWebHandler webHandler,
                                       RouteLocator routeLocator,
                                       GlobalCorsProperties globalCorsProperties,
                                       Environment environment) {
        super(webHandler, routeLocator, globalCorsProperties, environment);
    }

    @Override
    protected @NonNull Mono<?> getHandlerInternal(ServerWebExchange exchange) {
        // 父类返回 Mono<?>，通配符会让 switchIfEmpty 的类型推断失败，这里收敛成 Mono<Object>
        Mono<Object> matched = super.getHandlerInternal(exchange).cast(Object.class);
        return matched.switchIfEmpty(Mono.defer(() -> {
            if (log.isWarnEnabled()) {
                log.warn("rejected: no route matched. path={}, method={}, ip={}",
                        exchange.getRequest().getURI().getPath(),
                        exchange.getRequest().getMethod(),
                        clientIp(exchange));
            }
            return Mono.just((Object) (WebHandler) this::writeBlankNotFound);
        }));
    }

    private Mono<Void> writeBlankNotFound(ServerWebExchange exchange) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.NOT_FOUND);
        return response.setComplete();
    }

    private String clientIp(ServerWebExchange exchange) {
        String forwarded = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded;
        }
        return exchange.getRequest().getRemoteAddress() != null
                ? exchange.getRequest().getRemoteAddress().getAddress().getHostAddress()
                : "unknown";
    }
}
