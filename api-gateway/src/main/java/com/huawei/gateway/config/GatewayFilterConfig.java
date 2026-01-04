package com.huawei.gateway.config;

import com.huawei.gateway.filter.JwtAuthenticationFilter;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

/**
 * Gateway Filter Configuration.
 *
 * Configures global filters that apply to all routes.
 * JWT authentication filter runs before routing to validate tokens.
 */
@Configuration
public class GatewayFilterConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public GatewayFilterConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    /**
     * Registers JWT authentication as a global filter.
     * Runs with high precedence to validate tokens before other filters.
     */
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public GlobalFilter jwtGlobalFilter() {
        return (exchange, chain) ->
            jwtAuthenticationFilter.apply(new JwtAuthenticationFilter.Config())
                    .filter(exchange, chain);
    }
}
                  