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

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Slack integration for alert notifications.
 *
 * Sends rich formatted messages via Slack Incoming Webhooks.
 * Supports Block Kit for structured, interactive messages.
 */
@Service
@SuppressWarnings({
    "null",       // RestTemplate and notification fields are non-null
    "java:S3457"  // Slack markdown requires literal \n, not platform line separator
})
public class SlackService implements AlertIntegration {

    private static final Logger log = LoggerFactory.getLogger(SlackService.class);
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String MRKDWN_TYPE = "mrkdwn";

    @Value("${alerts.slack.enabled:false}")
    private boolean enabled;

    @Value("${alerts.slack.webhook-url:}")
    private String webhookUrl;

    @Value("${alerts.slack.channel:}")
    private String defaultChannel;

    @Value("${alerts.slack.username:Base Station Alerts}")
    private String username;

    @Value("${alerts.slack.icon-emoji::satellite:}")
    private String iconEmoji;

    private final RestTemplate restTemplate;

    public SlackService() {
        this.restTemplate = new RestTemplate();
    }

    @Override
    public String getName() {
        return "slack";
    }

    @Override
    public boolean isEnabled() {
        return enabled && webhookUrl != null && !webhookUrl.isBlank();
    }

    @Override
    public CompletableFuture<AlertResult> sendAlert(Notification notification) {
        return CompletableFuture.supplyAsync(() -> {
            if (!isEnabled()) {
                return AlertResult.failure(getName(), "Slack integration is not enabled");
            }

            try {
                Map<String, Object> payload = buildAlertPayload(notification);
                HttpEntity<Map<String, Object>> request = createRequest(payload);

                String response = restTemplate.postForObject(webhookUrl, request, String.class);

                if ("ok".equals(response)) {
                    log.info("Slack alert sent: notification={}", notification.getId());
                    return AlertResult.success(getName(), notification.getId().toString());
                } else {
                    log.error("Slack alert failed: {}", response);
                    return AlertResult.failure(getName(), response);
                }
            } catch (Exception e) {
                log.error("Failed to send Slack alert: {}", e.getMessage(), e);
                return AlertResult.failure(getName(), e.getMessage());
            }
        });
    }

    @Override
    public CompletableFuture<AlertResult> resolveAlert(Notification notification) {
        return CompletableFuture.supplyAsync(() -> {
            if (!isEnabled()) {
                return AlertResult.failure(getName(), "Slack integration is not enabled");
            }

            try {
                Map<String, Object> payload = buildResolvePayload(notification);
                HttpEntity<Map<String, Object>> request = createRequest(payload);

                String response = restTemplate.postForObject(webhookUrl, request, String.class);

                if ("ok".equals(response)) {
                    log.info("Slack resolve notification sent: notification={}", notification.getId());
                    return AlertResult.success(getName(), notification.getId().toString());
                } else {
                    return AlertResult.failure(getName(), response);
                }
            } catch (Exception e) {
                log.error("Failed to send Slack resolve notification: {}", e.getMessage(), e);
                return AlertResult.failure(getName(), e.getMessage());
            }
        });
    }

    @Override
    public boolean testConnection() {
        if (!isEnabled()) {
            return false;
        }

        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("text", "Test connection from Base Station Platform");

            HttpEntity<Map<String, Object>> request = createRequest(payload);
            String response = restTemplate.postForObject(webhookUrl, request, String.class);

            return "ok".equals(response);
        } catch (Exception e) {
            log.error("Slack connection test failed: {}", e.getMessage());
            return false;
        }
    }

    private Map<String, Object> buildAlertPayload(Notification notification) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("username", username);
        payload.put("icon_emoji", iconEmoji);

        if (defaultChannel != null && !defaultChannel.isBlank()) {
            payload.put("channel", defaultChannel);
        }

        // Build Block Kit blocks for rich formatting
        List<Map<String, Object>> blocks = new ArrayList<>();

        // Header block
        blocks.add(createHeaderBlock(notification));

        // Divider
        blocks.add(Map.of("type", "divider"));

        // Alert details section
        blocks.add(createDetailsSection(notification));

        // Context block with metadata
        blocks.add(createContextBlock(notification));

        payload.put("blocks", blocks);

        // Fallback text for notifications
        payload.put("text", String.format("[%s] Station %s: %s",
                notification.getType().name(),
                notification.getStationId(),
                notification.getMessage()));

        return payload;
    }

    private Map<String, Object> buildResolvePayload(Notification notification) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("username", username);
        payload.put("icon_emoji", ":white_check_mark:");

        if (defaultChannel != null && !defaultChannel.isBlank()) {
            payload.put("channel", defaultChannel);
        }

        List<Map<String, Object>> blocks = new ArrayList<>();

        // Resolved header
        Map<String, Object> header = new HashMap<>();
        header.put("type", "header");
        header.put("text", Map.of(
                "type", "plain_text",
                "text", "Resolved: Station " + notification.getStationId(),
                "emoji", true
        ));
        blocks.add(header);

        // Details section
        Map<String, Object> section = new HashMap<>();
        section.put("type", "section");
        section.put("text", Map.of(
                "type", MRKDWN_TYPE,
                "text", String.format("*Alert Resolved*\n%s", notification.getMessage())
        ));
        blocks.add(section);

        payload.put("blocks", blocks);
        payload.put("text", String.format("[RESOLVED] Station %s: %s",
                notification.getStationId(), notification.getMessage()));

        return payload;
    }

    private Map<String, Object> createHeaderBlock(Notification notification) {
        Map<String, Object> header = new HashMap<>();
        header.put("type", "header");

        String emoji = getTypeEmoji(notification.getType());
        String headerText = String.format("%s %s Alert: Station %s",
                emoji, notification.getType().name(), notification.getStationId());

        header.put("text", Map.of(
                "type", "plain_text",
                "text", headerText,
                "emoji", true
        ));

        return header;
    }

    private Map<String, Object> createDetailsSection(Notification notification) {
        Map<String, Object> section = new HashMap<>();
        section.put("type", "section");

        List<Map<String, Object>> fields = new ArrayList<>();

        // Message field
        fields.add(Map.of(
                "type", MRKDWN_TYPE,
                "text", String.format("*Message*\n%s", notification.getMessage())
        ));

        // Station ID field
        fields.add(Map.of(
                "type", MRKDWN_TYPE,
                "text", String.format("*Station ID*\n%s", notification.getStationId())
        ));

        // Severity field
        fields.add(Map.of(
                "type", MRKDWN_TYPE,
                "text", String.format("*Severity*\n%s", getSeverityLabel(notification.getType()))
        ));

        // Status field
        fields.add(Map.of(
                "type", MRKDWN_TYPE,
                "text", String.format("*Status*\n%s", notification.getStatus().name())
        ));

        section.put("fields", fields);

        return section;
    }

    private Map<String, Object> createContextBlock(Notification notification) {
        Map<String, Object> context = new HashMap<>();
        context.put("type", "context");

        List<Map<String, Object>> elements = new ArrayList<>();

        // Timestamp
        String timeStr = notification.getCreatedAt() != null
                ? notification.getCreatedAt().format(TIME_FORMATTER)
                : "Unknown";

        elements.add(Map.of(
                "type", MRKDWN_TYPE,
                "text", String.format(":clock1: %s", timeStr)
        ));

        // Notification ID
        elements.add(Map.of(
                "type", MRKDWN_TYPE,
                "text", String.format("ID: %s", notification.getId())
        ));

        context.put("elements", elements);

        return context;
    }

    private HttpEntity<Map<String, Object>> createRequest(Map<String, Object> payload) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(payload, headers);
    }

    private String getTypeEmoji(NotificationType type) {
        return switch (type) {
            case ALERT, ERROR -> ":rotating_light:";
            case WARNING -> ":warning:";
            case MAINTENANCE -> ":wrench:";
            case INFO -> ":information_source:";
        };
    }

    private String getSeverityLabel(NotificationType type) {
        return switch (type) {
            case ALERT, ERROR -> ":red_circle: Critical";
            case WARNING -> ":large_orange_circle: Warning";
            case MAINTENANCE -> ":large_blue_circle: Maintenance";
            case INFO -> ":white_circle: Info";
        };
    }
}
