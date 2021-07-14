# SpringCloudGateWay

### 使用SpringCloudGateWay实现动态网关，类似Nginx反向代理



#### 环境

| 组件         | 版本           |
| ------------ | -------------- |
| JDK          | ８             |
| Spring Cloud | Hoxton.RELEASE |

#### POM

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>org.example</groupId>
    <artifactId>SpringGateWay</artifactId>
    <version>1.0-SNAPSHOT</version>

    <properties>
        <maven.compiler.source>8</maven.compiler.source>
        <maven.compiler.target>8</maven.compiler.target>
        <spring-cloud.version>Hoxton.RELEASE</spring-cloud.version>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-gateway</artifactId>
        </dependency>
    </dependencies>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <!-- spring cloud  -->
                <groupId>org.springframework.cloud</groupId>
                <artifactId>spring-cloud-dependencies</artifactId>
                <version>${spring-cloud.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>

    <repositories>
        <repository>
            <id>aliyunmaven</id>
            <name>aliyunmaven</name>
            <url>https://maven.aliyun.com/repository/public</url>
            <layout>default</layout>
            <releases>
                <enabled>true</enabled>
            </releases>
            <snapshots>
                <enabled>true</enabled>
            </snapshots>
        </repository>
        <repository>
            <id>spring-milestones</id>
            <name>Spring Milestones</name>
            <url>https://repo.spring.io/milestone</url>
            <snapshots>
                <enabled>false</enabled>
            </snapshots>
        </repository>
    </repositories>
</project>
```

#### application.yml

```yml
spring:
  cloud:
    gateway:
      routes:
        - id: api
          uri: https://wwww.baidu.com
          predicates:
            - Header=host,([a-zA-Z0-9]+\.[a-zA-Z0-9]+\.[a-zA-Z0-9]+)
          filters:
            - UriHostPlaceholderFilter

```

#### Application

```java
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

```


