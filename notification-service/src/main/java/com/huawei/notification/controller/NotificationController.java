package com.huawei.notification.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.huawei.notification.model.Notification;
import com.huawei.notification.model.NotificationStatus;
import com.huawei.notification.model.NotificationType;
import com.huawei.notification.service.NotificationService;

@RestController
@RequestMapping("/api/v1/notifications")
@SuppressWarnings("null")
public class NotificationController {

    private final NotificationService service;

    public NotificationController(NotificationService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<Notification> createNotification(
            @RequestParam Long stationId,
            @RequestParam String message,
            @RequestParam NotificationType type) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.createNotification(stationId, message, type));
    }

    @PostMapping("/{id}/send")
    public ResponseEntity<Map<String, String>> sendNotification(@PathVariable Long id) {
        try {
            service.sendNotification(id);
            return ResponseEntity.ok(Map.of("status", "sent", "message", "Notification sent successfully"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    @PostMapping("/process-pending")
    public ResponseEntity<Map<String, String>> processPendingNotifications() {
        service.processPendingNotifications();
        return ResponseEntity.ok(Map.of("status", "processing", "message", "Processing pending notifications"));
    }

    @GetMapping("/station/{stationId}")
    public ResponseEntity<List<Notification>> getNotificationsByStation(@PathVariable Long stationId) {
        return ResponseEntity.ok(service.getNotificationsByStation(stationId));
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
