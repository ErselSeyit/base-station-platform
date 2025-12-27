package com.huawei.notification.listener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import com.huawei.notification.config.RabbitMQConfig;
import com.huawei.notification.dto.AlertEvent;
import com.huawei.notification.model.NotificationType;
import com.huawei.notification.service.NotificationService;

/**
 * RabbitMQ listener for alert events from monitoring service.
 */
@Component
public class AlertEventListener {

    private static final Logger log = LoggerFactory.getLogger(AlertEventListener.class);
    
    private final NotificationService notificationService;

    public AlertEventListener(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @RabbitListener(queues = RabbitMQConfig.NOTIFICATION_QUEUE)
    public void handleAlertEvent(AlertEvent alertEvent) {
        log.info("Received alert event: ruleId={}, stationId={}, severity={}", 
                alertEvent.getAlertRuleId(), alertEvent.getStationId(), alertEvent.getSeverity());
        
        try {
            // Map alert severity to notification type
            NotificationType notificationType = mapSeverityToNotificationType(alertEvent.getSeverity());
            
            // Create notification from alert event
            String notificationMessage = String.format("[%s] %s - %s (Value: %.2f, Threshold: %.2f)",
                    alertEvent.getAlertRuleName(),
                    alertEvent.getMessage(),
                    alertEvent.getStationName() != null ? alertEvent.getStationName() : "Station " + alertEvent.getStationId(),
                    alertEvent.getMetricValue(),
                    alertEvent.getThreshold());
            
            notificationService.createNotification(
                    alertEvent.getStationId(),
                    notificationMessage,
                    notificationType
            );
            
            log.info("Notification created from alert event: ruleId={}, stationId={}", 
                    alertEvent.getAlertRuleId(), alertEvent.getStationId());
        } catch (Exception e) {
            log.error("Failed to process alert event: ruleId={}, stationId={}", 
                    alertEvent.getAlertRuleId(), alertEvent.getStationId(), e);
            throw e; // Re-throw to trigger RabbitMQ retry mechanism
        }
    }

    private NotificationType mapSeverityToNotificationType(String severity) {
        if (severity == null) {
            return NotificationType.ALERT;
        }
        
        return switch (severity.toUpperCase()) {
            case "CRITICAL" -> NotificationType.ERROR;
            case "WARNING" -> NotificationType.WARNING;
            case "INFO" -> NotificationType.INFO;
            default -> NotificationType.ALERT;
        };
    }
}
