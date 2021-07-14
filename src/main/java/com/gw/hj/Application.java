package com.gw.hj;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

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
}
