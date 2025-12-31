package com.huawei.monitoring.listener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import com.huawei.monitoring.dto.MetricDataDTO;
import com.huawei.monitoring.event.MetricRecordedEvent;
import com.huawei.monitoring.service.AlertingService;
import com.huawei.monitoring.websocket.MetricsWebSocketHandler;

/**
 * Listens for MetricRecordedEvent and handles side effects asynchronously.
 * This separates concerns from MonitoringService, following Single Responsibility Principle.
 *
 * Side effects handled:
 * - Real-time WebSocket broadcasting to connected clients
 * - Alert rule evaluation for threshold violations
 */
@Component
public class MetricEventListener {

    private static final Logger log = LoggerFactory.getLogger(MetricEventListener.class);

    private final MetricsWebSocketHandler webSocketHandler;
    private final AlertingService alertingService;

    public MetricEventListener(
            MetricsWebSocketHandler webSocketHandler,
            AlertingService alertingService) {
        this.webSocketHandler = webSocketHandler;
        this.alertingService = alertingService;
    }

    /**
     * Broadcasts the metric to all connected WebSocket clients for real-time updates.
     * Runs asynchronously to not block the main transaction.
     */
    @Async
    @EventListener
    public void handleMetricBroadcast(MetricRecordedEvent event) {
        MetricDataDTO metric = event.getMetric();
        try {
            webSocketHandler.broadcastMetric(metric);
            log.debug("Broadcasted metric to WebSocket clients: station={}, type={}",
                    metric.getStationId(), metric.getMetricType());
        } catch (Exception e) {
            log.error("Failed to broadcast metric to WebSocket clients: {}", e.getMessage(), e);
            // Non-fatal: WebSocket broadcast failure shouldn't break metric recording
        }
    }

    /**
     * Evaluates alert rules against the recorded metric.
     * Runs asynchronously to not block the main transaction.
     */
    @Async
    @EventListener
    @SuppressWarnings("null") // MetricRecordedEvent guarantees non-null metric
    public void handleAlertEvaluation(MetricRecordedEvent event) {
        MetricDataDTO metric = event.getMetric();
        try {
            alertingService.evaluateMetric(metric);
            log.debug("Evaluated alert rules for metric: station={}, type={}",
                    metric.getStationId(), metric.getMetricType());
        } catch (Exception e) {
            log.error("Failed to evaluate alert rules for metric: {}", e.getMessage(), e);
            // Non-fatal: Alert evaluation failure shouldn't break metric recording
        }
    }
}