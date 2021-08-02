package com.gw.hj;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.reactivestreams.Publisher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.util.Objects;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR;

@SpringBootApplication
public class Application {

    private static final String HOST = "host";

    public static void main(String[] args) {
        SpringApplication.run(Application.class);
    }

    @Component
    public static final class UriHostPlaceholderFilter extends AbstractGatewayFilterFactory {

        @Override
        public GatewayFilter apply(Object config) {
            return (exchange, chain) -> {
                if (!exchange.getRequest().getHeaders().containsKey(HOST)) {
                    return chain.filter(exchange);
                }
                Route route = exchange.getAttribute(GATEWAY_ROUTE_ATTR); // 获取路由。
                if (route == null) {
                    return chain.filter(exchange);
                }
                URI uri = route.getUri();
                String host = exchange.getRequest().getHeaders().getFirst(HOST);
                if (host == null) {
                    return chain.filter(exchange);
                }
                URI newUri = UriComponentsBuilder.fromUri(uri).host(host).build().toUri(); // 重构URI。
                Route newRoute = Route.async()
                        .id(route.getId())
                        .uri(newUri)
                        .order(route.getOrder())
                        .asyncPredicate(route.getPredicate())
                        .filters(route.getFilters())
                        .build(); // 重构路由。
                exchange.getAttributes().put(GATEWAY_ROUTE_ATTR, newRoute);
                return chain.filter(exchange);
            };
        }
    }

    @Component
    @Slf4j
    public static final class CachePlaceholderFilter extends AbstractGatewayFilterFactory {

        @Autowired
        private StringRedisTemplate stringRedisTemplate;

        @Value("${xa.gateway.prefix}")
        private String keyPrefix;

        @Override
        public GatewayFilter apply(Object config) {
            return new CacheFilter(keyPrefix, stringRedisTemplate);
        }

        private static class CacheFilter implements GatewayFilter, Ordered {

            private StringRedisTemplate stringRedisTemplate;
            private String keyPrefix;

            public CacheFilter(String keyPrefix, StringRedisTemplate stringRedisTemplate) {
                this.stringRedisTemplate = stringRedisTemplate;
                this.keyPrefix = keyPrefix;
            }

            @Override
            public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
                ServerHttpRequest request = exchange.getRequest();
                ServerHttpResponse response = exchange.getResponse();
                String clientName = request.getPath().subPath(2).value().substring(1);
                System.out.println("path = " + exchange.getRequest().getPath().subPath(2));

                String cacheKey = getKey(clientName);
                if (stringRedisTemplate.hasKey(cacheKey)) {
                    System.out.println("clientName =  " + clientName + " has cache ");
                    response.getHeaders().add("Content-Type", "application/json;charset=UTF-8");
                    byte[] data = stringRedisTemplate.opsForValue().get(cacheKey).getBytes();
                    response.setStatusCode(HttpStatus.OK);
                    DataBuffer buffer = response.bufferFactory().wrap(data);
                    return response.writeWith(Mono.just(buffer));
                }
                final ServerHttpResponse mutatedHttpResponse = getServerHttpResponse(exchange, cacheKey);
                return chain.filter(exchange.mutate().response(mutatedHttpResponse).build());
            }

            @Override
            public int getOrder() {
                return -2;
            }

            private String getKey(String clientName) {
                return keyPrefix + clientName;
            }

            private ServerHttpResponse getServerHttpResponse(ServerWebExchange exchange, String cacheKey) {
                final ServerHttpResponse originalResponse = exchange.getResponse();
                final DataBufferFactory dataBufferFactory = originalResponse.bufferFactory();

                return new ServerHttpResponseDecorator(originalResponse) {
                    @NonNull
                    @Override
                    public Mono<Void> writeWith(@NonNull Publisher<? extends DataBuffer> body) {
                        if (body instanceof Flux) {
                            final Flux<? extends DataBuffer> flux = (Flux<? extends DataBuffer>) body;
                            return super.writeWith(flux.buffer().map(dataBuffers -> {
                                final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                                dataBuffers.forEach(dataBuffer -> {
                                    final byte[] responseContent = new byte[dataBuffer.readableByteCount()];
                                    dataBuffer.read(responseContent);
                                    try {
                                        outputStream.write(responseContent);
                                    } catch (IOException e) {
                                        throw new RuntimeException("Error while reading response stream", e);
                                    }
                                });
                                if (Objects.requireNonNull(getStatusCode()).is2xxSuccessful()) {
                                    stringRedisTemplate.opsForValue().set(cacheKey, outputStream.toString());
                                }
                                return dataBufferFactory.wrap(outputStream.toByteArray());
                            }));
                        }
                        return super.writeWith(body);
                    }
                };
            }
        }
    }
}
