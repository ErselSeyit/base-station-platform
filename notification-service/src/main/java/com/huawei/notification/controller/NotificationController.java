package com.huawei.notification.controller;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.huawei.notification.exception.NotificationException;
import com.huawei.notification.model.Notification;
import com.huawei.notification.model.NotificationStatus;
import com.huawei.notification.model.NotificationType;
import com.huawei.notification.service.NotificationService;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private static final String STATUS_KEY = "status";
    private static final String MESSAGE_KEY = "message";

    private final NotificationService service;

    public NotificationController(NotificationService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public ResponseEntity<Notification> createNotification(
            @RequestParam Long stationId,
            @RequestParam String message,
            @RequestParam NotificationType type) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.createNotification(
                        Objects.requireNonNull(stationId, "Station ID cannot be null"),
                        Objects.requireNonNull(message, "Message cannot be null"),
                        Objects.requireNonNull(type, "Notification type cannot be null")));
    }

    @PostMapping("/{id}/send")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public ResponseEntity<Map<String, String>> sendNotification(@PathVariable Long id) {
        try {
            service.sendNotification(Objects.requireNonNull(id, "Notification ID cannot be null"));
            return ResponseEntity.ok(Map.of(STATUS_KEY, "sent", MESSAGE_KEY, "Notification sent successfully"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of(STATUS_KEY, "error", MESSAGE_KEY, e.getMessage()));
        } catch (NotificationException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(STATUS_KEY, "error", MESSAGE_KEY, e.getMessage()));
        }
    }

    @PostMapping("/process-pending")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> processPendingNotifications() {
        // Start processing asynchronously (fire and forget)
        // The method now returns CompletableFuture, so we don't block
        service.processPendingNotifications()
                .thenRun(() -> {
                    // Optional: Log completion (this runs in async thread)
                    // Could also publish event or update status here
                })
                .exceptionally(throwable -> {
                    // Log any errors that occur during processing
                    org.slf4j.LoggerFactory.getLogger(NotificationController.class)
                            .error("Error processing pending notifications", throwable);
                    return null;
                });
        
        // Return immediately - processing happens in background
        return ResponseEntity.accepted()
                .body(Map.of(STATUS_KEY, "processing", MESSAGE_KEY, "Processing pending notifications in background"));
    }

    @GetMapping("/station/{stationId}")
    public ResponseEntity<List<Notification>> getNotificationsByStation(@PathVariable Long stationId) {
        return ResponseEntity.ok(service.getNotificationsByStation(
                Objects.requireNonNull(stationId, "Station ID cannot be null")));
    }

    @GetMapping
    public ResponseEntity<List<Notification>> getNotifications(
            @RequestParam(required = false) NotificationStatus status) {
        if (status != null) {
            return ResponseEntity.ok(service.getNotificationsByStatus(status));
        }
        return ResponseEntity.ok(service.getAllNotifications());
    }
}
