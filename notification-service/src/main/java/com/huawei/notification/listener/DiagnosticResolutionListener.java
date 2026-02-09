package com.huawei.notification.listener;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import com.huawei.common.dto.DiagnosticResolutionEvent;
import com.huawei.notification.config.RabbitMQConfig;
import com.huawei.notification.service.NotificationService;

/**
 * RabbitMQ listener for diagnostic resolution events from monitoring service.
 * When AI diagnostics resolves a problem, this listener marks related notifications as RESOLVED.
 */
@Component
public class DiagnosticResolutionListener {

    private static final Logger log = LoggerFactory.getLogger(DiagnosticResolutionListener.class);

    private final NotificationService notificationService;

    public DiagnosticResolutionListener(NotificationService notificationService) {
        this.notificationService = notificationService;
        log.info("DiagnosticResolutionListener created");
    }

    @PostConstruct
    public void init() {
        log.info("DiagnosticResolutionListener initialized, listening on queue: {}",
                RabbitMQConfig.DIAGNOSTIC_RESOLUTION_QUEUE);
    }

    @RabbitListener(queues = RabbitMQConfig.DIAGNOSTIC_RESOLUTION_QUEUE)
    public void handleResolutionEvent(DiagnosticResolutionEvent event) {
        log.info("Received diagnostic resolution event: sessionId={}, problemId={}, wasEffective={}",
                event.sessionId(), event.problemId(), event.wasEffective());

        try {
            // Only resolve notifications if the solution was effective
            if (event.wasEffective()) {
                int resolved = notificationService.resolveByProblemId(event.problemId());
                log.info("Resolved {} notifications for problemId={}", resolved, event.problemId());
            } else {
                log.debug("Skipping notification resolution - solution was not effective for problemId={}",
                        event.problemId());
            }
        } catch (Exception e) {
            log.error("Failed to process resolution event for problemId={}: {}",
                    event.problemId(), e.getMessage(), e);
            // Don't rethrow - we don't want to reject the message and cause infinite retries
        }
    }
}
