package com.huawei.notification.controller;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static com.huawei.common.constants.JsonResponseKeys.KEY_MESSAGE;
import static com.huawei.common.constants.JsonResponseKeys.KEY_STATUS;

import com.huawei.common.security.Roles;
import com.huawei.notification.dto.NotificationRequest;
import com.huawei.notification.dto.NotificationResponse;
import com.huawei.notification.exception.NotificationException;
import com.huawei.notification.exception.NotificationNotFoundException;
import com.huawei.notification.model.NotificationStatus;
import com.huawei.notification.service.NotificationService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/notifications")
@Tag(name = "Notifications", description = "Notification management and delivery")
@SecurityRequirement(name = "bearerAuth")
@SuppressWarnings("null") // @Valid ensures non-null values from validated DTOs
public class NotificationController {

    private static final Logger log = LoggerFactory.getLogger(NotificationController.class);

    private final NotificationService service;

    public NotificationController(NotificationService service) {
        this.service = service;
    }

    @Operation(summary = "Create notification", description = "Creates a new notification for a base station")
    @ApiResponse(responseCode = "201", description = "Notification created",
            content = @Content(schema = @Schema(implementation = NotificationResponse.class)))
    @ApiResponse(responseCode = "400", description = "Invalid parameters")
    @PostMapping
    @PreAuthorize(Roles.HAS_OPERATOR)
    public ResponseEntity<NotificationResponse> createNotification(
            @Parameter(description = "Notification data") @Valid @RequestBody NotificationRequest request) {
        // @Valid ensures request fields are non-null per DTO validation constraints
        var notification = service.createNotification(
                request.getStationId(),
                request.getMessage(),
                request.getType());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(NotificationResponse.fromEntity(notification));
    }

    @Operation(summary = "Send notification", description = "Sends a specific notification immediately")
    @ApiResponse(responseCode = "200", description = "Notification sent")
    @ApiResponse(responseCode = "404", description = "Notification not found")
    @ApiResponse(responseCode = "500", description = "Failed to send notification")
    @PostMapping("/{id}/send")
    @PreAuthorize(Roles.HAS_OPERATOR)
    public ResponseEntity<Map<String, String>> sendNotification(
            @Parameter(description = "Notification ID") @PathVariable Long id) {
        try {
            service.sendNotification(id);
            return ResponseEntity.ok(Map.of(KEY_STATUS, "sent", KEY_MESSAGE, "Notification sent successfully"));
        } catch (NotificationNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of(KEY_STATUS, "error", KEY_MESSAGE, e.getMessage()));
        } catch (NotificationException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(KEY_STATUS, "error", KEY_MESSAGE, e.getMessage()));
        }
    }

    @Operation(summary = "Process pending notifications",
            description = "Triggers async processing of all pending notifications")
    @ApiResponse(responseCode = "202", description = "Processing started")
    @PostMapping("/process-pending")
    @PreAuthorize(Roles.HAS_ADMIN)
    public ResponseEntity<Map<String, String>> processPendingNotifications() {
        // Start processing asynchronously (fire and forget)
        service.processPendingNotifications()
                .thenRun(() -> log.debug("Completed processing pending notifications"))
                .exceptionally(throwable -> {
                    log.error("Error processing pending notifications", throwable);
                    return null;
                });

        // Return immediately - processing happens in background
        return ResponseEntity.accepted()
                .body(Map.of(KEY_STATUS, "processing", KEY_MESSAGE, "Processing pending notifications in background"));
    }

    @Operation(summary = "Get notifications by station",
            description = "Retrieves all notifications for a specific base station")
    @ApiResponse(responseCode = "200", description = "List of notifications")
    @GetMapping("/station/{stationId}")
    public ResponseEntity<List<NotificationResponse>> getNotificationsByStation(
            @Parameter(description = "Station ID") @PathVariable Long stationId) {
        var notifications = service.getNotificationsByStation(stationId);
        return ResponseEntity.ok(notifications.stream()
                .map(NotificationResponse::fromEntity)
                .toList());
    }

    @Operation(summary = "Get all notifications",
            description = "Retrieves notifications, optionally filtered by status")
    @ApiResponse(responseCode = "200", description = "List of notifications")
    @GetMapping
    public ResponseEntity<List<NotificationResponse>> getNotifications(
            @Parameter(description = "Filter by status (optional)") @RequestParam(required = false) @Nullable NotificationStatus status) {
        var notifications = status != null
                ? service.getNotificationsByStatus(status)
                : service.getAllNotifications();
        return ResponseEntity.ok(notifications.stream()
                .map(NotificationResponse::fromEntity)
                .toList());
    }
}
