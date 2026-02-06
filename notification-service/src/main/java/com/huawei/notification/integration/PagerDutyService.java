package com.huawei.notification.integration;

import com.huawei.notification.model.Notification;
import com.huawei.notification.model.NotificationType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * PagerDuty integration for incident management.
 *
 * Sends alerts via PagerDuty Events API v2.
 * Supports creating, acknowledging, and resolving incidents.
 */
@Service
public class PagerDutyService implements AlertIntegration {

    private static final Logger log = LoggerFactory.getLogger(PagerDutyService.class);
    private static final String PAGERDUTY_EVENTS_URL = "https://events.pagerduty.com/v2/enqueue";
    private static final String DEDUP_KEY_FIELD = "dedup_key";

    @Value("${alerts.pagerduty.enabled:false}")
    private boolean enabled;

    @Value("${alerts.pagerduty.routing-key:}")
    private String routingKey;

    @Value("${alerts.pagerduty.source:base-station-platform}")
    private String source;

    private final RestTemplate restTemplate;

    public PagerDutyService() {
        this.restTemplate = new RestTemplate();
    }

    @Override
    public String getName() {
        return "pagerduty";
    }

    @Override
    public boolean isEnabled() {
        return enabled && routingKey != null && !routingKey.isBlank();
    }

    @Override
    public CompletableFuture<AlertResult> sendAlert(Notification notification) {
        return CompletableFuture.supplyAsync(() -> {
            if (!isEnabled()) {
                return AlertResult.failure(getName(), "PagerDuty integration is not enabled");
            }

            try {
                Map<String, Object> payload = buildTriggerPayload(notification);
                HttpEntity<Map<String, Object>> request = createRequest(payload);

                @SuppressWarnings("unchecked")
                Map<String, Object> response = restTemplate.postForObject(
                        PAGERDUTY_EVENTS_URL, request, Map.class);

                if (response != null && "success".equals(response.get("status"))) {
                    String dedupKey = (String) response.get(DEDUP_KEY_FIELD);
                    log.info("PagerDuty alert created: dedupKey={}, notification={}",
                            dedupKey, notification.getId());
                    return AlertResult.success(getName(), dedupKey);
                } else {
                    String message = response != null ? response.toString() : "No response";
                    log.error("PagerDuty alert failed: {}", message);
                    return AlertResult.failure(getName(), message);
                }
            } catch (Exception e) {
                log.error("Failed to send PagerDuty alert: {}", e.getMessage(), e);
                return AlertResult.failure(getName(), e.getMessage());
            }
        });
    }

    @Override
    public CompletableFuture<AlertResult> resolveAlert(Notification notification) {
        return CompletableFuture.supplyAsync(() -> {
            if (!isEnabled()) {
                return AlertResult.failure(getName(), "PagerDuty integration is not enabled");
            }

            try {
                Map<String, Object> payload = buildResolvePayload(notification);
                HttpEntity<Map<String, Object>> request = createRequest(payload);

                @SuppressWarnings("unchecked")
                Map<String, Object> response = restTemplate.postForObject(
                        PAGERDUTY_EVENTS_URL, request, Map.class);

                if (response != null && "success".equals(response.get("status"))) {
                    log.info("PagerDuty alert resolved: notification={}", notification.getId());
                    return AlertResult.success(getName(), (String) response.get(DEDUP_KEY_FIELD));
                } else {
                    return AlertResult.failure(getName(), "Failed to resolve alert");
                }
            } catch (Exception e) {
                log.error("Failed to resolve PagerDuty alert: {}", e.getMessage(), e);
                return AlertResult.failure(getName(), e.getMessage());
            }
        });
    }

    @Override
    public boolean testConnection() {
        // PagerDuty doesn't have a test endpoint, so we just verify configuration
        return isEnabled();
    }

    private Map<String, Object> buildTriggerPayload(Notification notification) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("routing_key", routingKey);
        payload.put("event_action", "trigger");
        payload.put(DEDUP_KEY_FIELD, generateDedupKey(notification));

        Map<String, Object> payloadData = new HashMap<>();
        payloadData.put("summary", notification.getMessage());
        payloadData.put("source", source);
        payloadData.put("severity", mapSeverity(notification.getType()));
        payloadData.put("timestamp", Instant.now().toString());
        payloadData.put("component", "station-" + notification.getStationId());
        payloadData.put("group", "base-stations");
        payloadData.put("class", notification.getType().name());

        Map<String, Object> customDetails = new HashMap<>();
        customDetails.put("station_id", notification.getStationId());
        customDetails.put("notification_id", notification.getId());
        customDetails.put("type", notification.getType().name());
        customDetails.put("created_at", notification.getCreatedAt().toString());
        payloadData.put("custom_details", customDetails);

        payload.put("payload", payloadData);

        return payload;
    }

    private Map<String, Object> buildResolvePayload(Notification notification) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("routing_key", routingKey);
        payload.put("event_action", "resolve");
        payload.put(DEDUP_KEY_FIELD, generateDedupKey(notification));
        return payload;
    }

    private HttpEntity<Map<String, Object>> createRequest(Map<String, Object> payload) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(payload, headers);
    }

    private String generateDedupKey(Notification notification) {
        // Use notification ID as dedup key for idempotency
        return "station-" + notification.getStationId() + "-" + notification.getId();
    }

    private String mapSeverity(NotificationType type) {
        return switch (type) {
            case ALERT, ERROR -> "critical";
            case WARNING -> "warning";
            case MAINTENANCE -> "info";
            case INFO -> "info";
        };
    }
}
