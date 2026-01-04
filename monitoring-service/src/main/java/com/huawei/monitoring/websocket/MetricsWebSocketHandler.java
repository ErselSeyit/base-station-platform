package com.huawei.monitoring.websocket;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArraySet;

import com.fasterxml.jackson.core.JsonProcessingException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huawei.monitoring.dto.MetricDataDTO;

/**
 * WebSocket handler for real-time metrics streaming.
 * 
 * Clients connect to /ws/metrics to receive live metric updates.
 * This enables dashboards to display real-time data without polling.
 */
@Component
public class MetricsWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(MetricsWebSocketHandler.class);

    private final CopyOnWriteArraySet<WebSocketSession> sessions = new CopyOnWriteArraySet<>();
    private final ObjectMapper objectMapper;

    public MetricsWebSocketHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(@NonNull WebSocketSession session) {
        sessions.add(session);
        log.info("WebSocket connected: {} (total: {})", session.getId(), sessions.size());
    }

    @Override
    public void afterConnectionClosed(@NonNull WebSocketSession session, @NonNull CloseStatus status) {
        sessions.remove(session);
        log.info("WebSocket disconnected: {} (total: {})", session.getId(), sessions.size());
    }

    @Override
    public void handleTransportError(@NonNull WebSocketSession session, @NonNull Throwable exception) {
        log.error("WebSocket error for session {}: {}", session.getId(), exception.getMessage());
        sessions.remove(session);
    }

    /**
     * Broadcasts a metric update to all connected clients.
     * Called by MonitoringService when a new metric is recorded.
     */
    public void broadcastMetric(MetricDataDTO metric) {
        if (sessions.isEmpty()) {
            return;
        }

        try {
            String json = Objects.requireNonNull(
                    objectMapper.writeValueAsString(metric),
                    "JSON serialization cannot return null");
            TextMessage message = new TextMessage(json);

            for (WebSocketSession session : sessions) {
                if (session.isOpen()) {
                    sendMessageToSession(session, message);
                }
            }
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize metric: {}", e.getMessage());
        }
    }

    private void sendMessageToSession(WebSocketSession session, @NonNull TextMessage message) {
        try {
            session.sendMessage(message);
        } catch (IOException e) {
            log.warn("Failed to send to session {}: {}", session.getId(), e.getMessage());
            sessions.remove(session);
        }
    }

    public int getActiveConnections() {
        return sessions.size();
    }
}
