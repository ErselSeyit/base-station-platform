package com.huawei.gateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;

import reactor.core.publisher.Mono;

/**
 * Configures rate limiting for the API Gateway.
 *
 * Provides multiple key resolvers:
 * - IP-based: Default limiter for unauthenticated requests
 * - User-based: Per-user limits for authenticated requests
 * - Combined: Uses user if authenticated, falls back to IP
 */
@Configuration
public class RateLimiterConfig {

    /**
     * Uses the client's IP address to identify who's making requests.
     * This way we can limit how many requests each IP can make per second.
     */
    @Bean
    public KeyResolver ipKeyResolver() {
        return exchange -> {
            var remoteAddress = exchange.getRequest().getRemoteAddress();
            String ip = "unknown";
            if (remoteAddress != null && remoteAddress.getAddress() != null) {
                ip = remoteAddress.getAddress().getHostAddress();
            }
            return Mono.just("ip:" + ip);
        };
    }

    /**
     * Uses the authenticated user's username for rate limiting.
     * Falls back to IP if no user is authenticated.
     */
    @Bean
    @Primary
    public KeyResolver userKeyResolver() {
        return exchange -> {
            // Try to get username from X-User-Name header (set by JWT filter)
            String username = exchange.getRequest().getHeaders().getFirst("X-User-Name");

            if (username != null && !username.isBlank()) {
                return Mono.just("user:" + username);
            }

            // Check for Authorization header to extract from token
            String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                // Use hash of token as key (can't decode here without JWT validator)
                String tokenHash = String.valueOf(authHeader.hashCode());
                return Mono.just("token:" + tokenHash);
            }

            // Fall back to IP for unauthenticated requests
            var remoteAddress = exchange.getRequest().getRemoteAddress();
            String ip = "unknown";
            if (remoteAddress != null && remoteAddress.getAddress() != null) {
                ip = remoteAddress.getAddress().getHostAddress();
            }
            return Mono.just("ip:" + ip);
        };
    }
}
