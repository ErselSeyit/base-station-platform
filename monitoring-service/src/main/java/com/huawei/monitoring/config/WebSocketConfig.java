package com.huawei.monitoring.config;

import java.util.Objects;

import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import com.huawei.monitoring.websocket.MetricsWebSocketHandler;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final MetricsWebSocketHandler metricsHandler;

    public WebSocketConfig(MetricsWebSocketHandler metricsHandler) {
        this.metricsHandler = metricsHandler;
    }

    @Override
    public void registerWebSocketHandlers(@NonNull WebSocketHandlerRegistry registry) {
        registry.addHandler(Objects.requireNonNull(metricsHandler, "Metrics handler cannot be null"),
                "/ws/metrics")
                .setAllowedOrigins("*");
    }
}
