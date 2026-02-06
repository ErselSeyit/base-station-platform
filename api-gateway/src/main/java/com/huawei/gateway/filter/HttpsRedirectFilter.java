package com.huawei.gateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import com.huawei.common.constants.HttpHeaders;

import reactor.core.publisher.Mono;

import java.net.URI;

/**
 * HTTPS Redirect Filter for API Gateway.
 *
 * Enforces HTTPS in production by redirecting HTTP requests to HTTPS.
 * Can be enabled/disabled via configuration.
 *
 * Configuration:
 * - security.https.enforce: Enable/disable HTTPS enforcement
 * - security.https.port: HTTPS port (default 443)
 */
@Component
public class HttpsRedirectFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(HttpsRedirectFilter.class);

    @Value("${security.https.enforce:false}")
    private boolean enforceHttps;

    @Value("${security.https.port:443}")
    private int httpsPort;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!enforceHttps) {
            return chain.filter(exchange);
        }

        ServerHttpRequest request = exchange.getRequest();
        URI uri = request.getURI();

        // Check if already HTTPS
        if ("https".equalsIgnoreCase(uri.getScheme())) {
            return chain.filter(exchange);
        }

        // Check X-Forwarded-Proto header (for load balancer/proxy scenarios)
        String forwardedProto = request.getHeaders().getFirst(HttpHeaders.HEADER_FORWARDED_PROTO);
        if ("https".equalsIgnoreCase(forwardedProto)) {
            return chain.filter(exchange);
        }

        // Skip health checks and actuator endpoints
        String path = uri.getPath();
        if (path.startsWith("/actuator") || path.equals("/health")) {
            return chain.filter(exchange);
        }

        // Redirect to HTTPS
        String httpsUrl = buildHttpsUrl(uri);
        log.debug("Redirecting HTTP to HTTPS: {} -> {}", uri, httpsUrl);

        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.MOVED_PERMANENTLY);
        response.getHeaders().set("Location", httpsUrl);
        return response.setComplete();
    }

    private String buildHttpsUrl(URI uri) {
        StringBuilder sb = new StringBuilder("https://");
        sb.append(uri.getHost());

        if (httpsPort != 443) {
            sb.append(":").append(httpsPort);
        }

        if (uri.getPath() != null) {
            sb.append(uri.getPath());
        }

        if (uri.getQuery() != null) {
            sb.append("?").append(uri.getQuery());
        }

        return sb.toString();
    }

    @Override
    public int getOrder() {
        // Run very early, before authentication
        return Ordered.HIGHEST_PRECEDENCE + 1;
    }
}
