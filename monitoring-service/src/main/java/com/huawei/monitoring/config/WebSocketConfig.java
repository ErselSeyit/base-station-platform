package com.huawei.monitoring.config;

import java.util.Objects;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import com.huawei.monitoring.websocket.MetricsWebSocketHandler;

/**
 * WebSocket configuration for real-time metrics streaming.
 * 
 * <h2>WebSocket Authentication Mechanism</h2>
 * 
 * <h3>Authentication Flow</h3>
 * <ol>
 *   <li><b>Gateway-Level Auth (Recommended)</b>: The API Gateway validates JWT tokens
 *       during the WebSocket upgrade request. If valid, the request is forwarded to
 *       this service with the authenticated principal.</li>
 *   <li><b>Handler-Level Auth (Fallback)</b>: If gateway doesn't validate, the
 *       {@link MetricsWebSocketHandler} must validate the token itself.</li>
 * </ol>
 * 
 * <h3>Token Transmission Options</h3>
 * <ul>
 *   <li><b>Authorization Header</b>: Send JWT as {@code Authorization: Bearer <token>}
 *       during the WebSocket handshake. Most secure but not supported by all browsers.</li>
 *   <li><b>Query Parameter</b>: Send as {@code ?token=<JWT>} in the WebSocket URL.
 *       Works in all browsers but token appears in server logs - use HTTPS only.</li>
 *   <li><b>First Message</b>: Send token as first message after connection.
 *       Handler must buffer messages until authenticated.</li>
 * </ul>
 * 
 * <h3>Client Connection Examples</h3>
 * <pre>{@code
 * // JavaScript - via query parameter (browser-compatible)
 * const token = localStorage.getItem('authToken');
 * const ws = new WebSocket(`wss://api-gateway/ws/metrics?token=${token}`);
 * 
 * // JavaScript - via first message (requires handler support)
 * const ws = new WebSocket('wss://api-gateway/ws/metrics');
 * ws.onopen = () => ws.send(JSON.stringify({ type: 'auth', token: token }));
 * }</pre>
 * 
 * <h3>Security Considerations</h3>
 * <ul>
 *   <li>Always use WSS (WebSocket Secure) in production - never WS over HTTP</li>
 *   <li>Tokens in query params appear in logs - configure log redaction</li>
 *   <li>Implement connection timeout for unauthenticated sessions</li>
 *   <li>Rate limit WebSocket connections per user/IP</li>
 *   <li>Validate token on each reconnect, not just initial connection</li>
 * </ul>
 * 
 * <h3>CORS Configuration</h3>
 * <p>{@code setAllowedOrigins("*")} is permissive for development. In production,
 * restrict to specific origins:</p>
 * <pre>{@code
 * .setAllowedOrigins("https://dashboard.example.com", "https://admin.example.com")
 * }</pre>
 * 
 * @see MetricsWebSocketHandler
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final MetricsWebSocketHandler metricsHandler;
    private final String[] allowedOrigins;

    public WebSocketConfig(
            MetricsWebSocketHandler metricsHandler,
            @Value("${websocket.allowed-origins:http://localhost:3000,http://frontend:80}") String allowedOriginsConfig) {
        this.metricsHandler = metricsHandler;
        this.allowedOrigins = allowedOriginsConfig.split(",");
    }

    /**
     * Registers WebSocket handlers for real-time metrics streaming.
     * 
     * <p>The {@code /ws/metrics} endpoint provides:
     * <ul>
     *   <li>Real-time metric updates as they are ingested</li>
     *   <li>Alert notifications when thresholds are exceeded</li>
     *   <li>Station status change events</li>
     * </ul>
     * 
     * @param registry the WebSocket handler registry
     */
    @Override
    @SuppressWarnings("null") // allowedOrigins is initialized in constructor and guaranteed non-null
    public void registerWebSocketHandlers(@NonNull WebSocketHandlerRegistry registry) {
        registry.addHandler(Objects.requireNonNull(metricsHandler, "Metrics handler cannot be null"),
                "/ws/metrics")
                // Origins configurable via websocket.allowed-origins property
                .setAllowedOrigins(allowedOrigins);
    }
}
