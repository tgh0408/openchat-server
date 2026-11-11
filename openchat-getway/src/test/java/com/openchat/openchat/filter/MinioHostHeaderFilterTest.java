package com.openchat.filter;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.boot.webflux.autoconfigure.WebFluxProperties;
import org.springframework.cloud.gateway.config.HttpClientProperties;
import org.springframework.cloud.gateway.filter.NettyRoutingFilter;
import org.springframework.cloud.gateway.filter.factory.PreserveHostHeaderGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.headers.HttpHeadersFilter;
import org.springframework.cloud.gateway.handler.predicate.PathRoutePredicateFactory;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.core.ResolvableType;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.client.HttpClient;
import reactor.netty.http.server.HttpServer;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.*;

class MinioHostHeaderFilterTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private final MinioHostHeaderFilter filter =
            new MinioHostHeaderFilter(new PreserveHostHeaderGatewayFilterFactory());

    @Test
    void preservesHostOnlyForMinioRoute() {
        MockServerWebExchange exchange = exchange("minio", HttpMethod.GET, "/openchat?location=", new byte[0]);
        filter.filter(exchange, next -> {
            assertSame(exchange, next);
            assertEquals("upload.example.test:99", next.getRequest().getHeaders().getFirst(HttpHeaders.HOST));
            assertTrue(next.getAttributeOrDefault(PRESERVE_HOST_HEADER_ATTRIBUTE, false));
            return Mono.empty();
        }).block(TIMEOUT);
        assertTrue(filter.getOrder() < NettyRoutingFilter.ORDER);
    }

    @Test
    void leavesOtherRoutesUnchanged() {
        for (String id : List.of("openchat-server", "wechat-callback", "openchat-socket", "test")) {
            MockServerWebExchange exchange = exchange(id, HttpMethod.GET, "/capi/test", new byte[0]);
            filter.filter(exchange, next -> Mono.empty()).block(TIMEOUT);
            assertFalse(exchange.getAttributeOrDefault(PRESERVE_HOST_HEADER_ATTRIBUTE, false));
        }
    }

    @Test
    void leavesUnmatchedRequestsUnchanged() {
        MockServerWebExchange exchange = exchange("minio", HttpMethod.GET, "/unknown", new byte[0]);
        exchange.getAttributes().remove(GATEWAY_ROUTE_ATTR);
        filter.filter(exchange, next -> Mono.empty()).block(TIMEOUT);
        assertFalse(exchange.getAttributeOrDefault(PRESERVE_HOST_HEADER_ATTRIBUTE, false));
    }

    @Test
    void bucketPredicateAlsoMatchesRegionLookup() {
        PathRoutePredicateFactory factory = new PathRoutePredicateFactory(new WebFluxProperties());
        PathRoutePredicateFactory.Config config = new PathRoutePredicateFactory.Config();
        config.setPatterns(List.of("/openchat/**"));
        assertTrue(factory.apply(config).test(exchange("minio", HttpMethod.GET, "/openchat?location=", new byte[0])));
        assertTrue(factory.apply(config).test(exchange("minio", HttpMethod.PUT, "/openchat/img/test.png", new byte[0])));
        assertFalse(factory.apply(config).test(exchange("minio", HttpMethod.GET, "/other?location=", new byte[0])));
    }

    @Test
    void nettyForwardsRegionLookupWithOriginalHostAndAuthorization() {
        forwardThroughNetty("minio", HttpMethod.GET, "/openchat?location=", new byte[0]);
    }

    @Test
    void nettyForwardsSignedPutWithoutChangingHostQueryOrBody() {
        forwardThroughNetty("minio", HttpMethod.PUT,
                "/openchat/img/space%20name.png?X-Amz-Credential=test%2Fscope&X-Amz-Signature=test-only",
                new byte[]{0, 1, 2, (byte) 255, 10, 13});
    }

    @Test
    void nettyKeepsDefaultUpstreamHostBehaviorForOtherRoutes() {
        forwardThroughNetty("openchat-server", HttpMethod.GET, "/capi/test", new byte[0]);
    }

    /** 仅连接临时回环 HTTP 服务，验证真实 Netty 转发，不启动 Spring、Nacos 或 MinIO。 */
    private void forwardThroughNetty(String routeId, HttpMethod method, String target, byte[] body) {
        AtomicReference<ReceivedRequest> received = new AtomicReference<>();
        DisposableServer upstream = HttpServer.create().host("127.0.0.1").port(0)
                .handle((request, response) -> request.receive().aggregate().asByteArray()
                        .defaultIfEmpty(new byte[0])
                        .flatMap(bytes -> {
                            received.set(new ReceivedRequest(request.requestHeaders().get(HttpHeaders.HOST),
                                    request.requestHeaders().get(HttpHeaders.AUTHORIZATION),
                                    request.method().name(), request.uri(), bytes));
                            return response.status(204).send().then();
                        }))
                .bindNow(TIMEOUT);
        try {
            MockServerWebExchange exchange = exchange(routeId, method, target, body);
            URI upstreamUri = URI.create("http://127.0.0.1:" + upstream.port() + target);
            exchange.getAttributes().put(GATEWAY_REQUEST_URL_ATTR, upstreamUri);
            DefaultListableBeanFactory beans = new DefaultListableBeanFactory();
            NettyRoutingFilter routing = new NettyRoutingFilter(HttpClient.create().keepAlive(false),
                    beans.getBeanProvider(ResolvableType.forClassWithGenerics(List.class, HttpHeadersFilter.class)),
                    new HttpClientProperties());
            filter.filter(exchange, next -> routing.filter(next, ignored -> Mono.empty())).block(TIMEOUT);

            ReceivedRequest actual = received.get();
            assertNotNull(actual);
            assertEquals("minio".equals(routeId) ? "upload.example.test:99" : upstreamUri.getRawAuthority(),
                    actual.host());
            assertEquals("AWS4-HMAC-SHA256 test-only", actual.authorization());
            assertEquals(method.name(), actual.method());
            assertEquals(target, actual.target());
            assertArrayEquals(body, actual.body());
        } finally {
            upstream.disposeNow(TIMEOUT);
        }
    }

    /** 构造虚构的公网请求，测试签名字段不使用真实凭据。 */
    private MockServerWebExchange exchange(String routeId, HttpMethod method, String target, byte[] body) {
        MockServerHttpRequest request = MockServerHttpRequest.method(method,
                        URI.create("https://upload.example.test:99" + target))
                .header(HttpHeaders.HOST, "upload.example.test:99")
                .header(HttpHeaders.AUTHORIZATION, "AWS4-HMAC-SHA256 test-only")
                .body(Flux.just(new DefaultDataBufferFactory().wrap(body)));
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        exchange.getAttributes().put(GATEWAY_ROUTE_ATTR, Route.async().id(routeId)
                .uri("http://127.0.0.1:9000").asyncPredicate(ignored -> Mono.just(true)).build());
        return exchange;
    }

    private record ReceivedRequest(String host, String authorization, String method, String target, byte[] body) {
    }
}
