package com.huawei.gateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import reactor.core.publisher.Mono;

/**
 * Configures rate limiting for the API Gateway using client IP addresses.
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
            return Mono.just(ip);
        };
    }
}
