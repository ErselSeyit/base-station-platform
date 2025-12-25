package com.huawei.monitoring.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import com.huawei.monitoring.websocket.MetricsWebSocketHandler;

@Configuration
@EnableWebSocket
@SuppressWarnings("null")
public class WebSocketConfig implements WebSocketConfigurer {

    private final MetricsWebSocketHandler metricsHandler;

    public WebSocketConfig(MetricsWebSocketHandler metricsHandler) {
        this.metricsHandler = metricsHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(metricsHandler, "/ws/metrics")
                .setAllowedOrigins("*");
    }
}

