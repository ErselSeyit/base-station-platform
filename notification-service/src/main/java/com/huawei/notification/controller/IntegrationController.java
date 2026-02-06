package com.huawei.notification.controller;

import com.huawei.common.security.Roles;
import com.huawei.notification.integration.AlertDispatcher;
import com.huawei.notification.integration.AlertIntegration;
import com.huawei.notification.repository.NotificationRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * REST controller for external alert integration management.
 */
@RestController
@RequestMapping("/api/v1/integrations")
@Tag(name = "Integrations", description = "External alert integration management")
@SuppressWarnings("null") // Optional and CompletableFuture operations guarantee non-null
public class IntegrationController {

    private final AlertDispatcher alertDispatcher;
    private final NotificationRepository notificationRepository;

    public IntegrationController(AlertDispatcher alertDispatcher,
                                  NotificationRepository notificationRepository) {
        this.alertDispatcher = alertDispatcher;
        this.notificationRepository = notificationRepository;
    }

    @Operation(
            summary = "Get integration status",
            description = "Returns status of all configured external integrations.")
    @ApiResponse(responseCode = "200", description = "Status retrieved successfully")
    @GetMapping("/status")
    @PreAuthorize(Roles.HAS_OPERATOR)
    public ResponseEntity<AlertDispatcher.IntegrationStatus> getStatus() {
        return ResponseEntity.ok(alertDispatcher.getStatus());
    }

    @Operation(
            summary = "Test all integration connections",
            description = "Tests connectivity to all configured integrations.")
    @ApiResponse(responseCode = "200", description = "Connection tests completed")
    @PostMapping("/test")
    @PreAuthorize(Roles.HAS_ADMIN)
    public ResponseEntity<Map<String, Boolean>> testConnections() {
        return ResponseEntity.ok(alertDispatcher.testAllConnections());
    }

    @Operation(
            summary = "Dispatch notification to all integrations",
            description = "Manually dispatches a notification to all enabled integrations.")
    @ApiResponse(responseCode = "200", description = "Dispatch completed")
    @ApiResponse(responseCode = "404", description = "Notification not found")
    @PostMapping("/dispatch/{notificationId}")
    @PreAuthorize(Roles.HAS_OPERATOR)
    public CompletableFuture<ResponseEntity<AlertDispatcher.DispatchResult>> dispatchNotification(
            @Parameter(description = "ID of the notification to dispatch")
            @PathVariable Long notificationId) {

        return notificationRepository.findById(notificationId)
                .map(notification -> alertDispatcher.dispatchAlert(notification)
                        .thenApply(ResponseEntity::ok))
                .orElse(CompletableFuture.completedFuture(ResponseEntity.notFound().build()));
    }

    @Operation(
            summary = "Dispatch to specific integration",
            description = "Dispatches a notification to a specific integration by name.")
    @ApiResponse(responseCode = "200", description = "Dispatch completed")
    @ApiResponse(responseCode = "404", description = "Notification not found")
    @PostMapping("/dispatch/{notificationId}/{integrationName}")
    @PreAuthorize(Roles.HAS_OPERATOR)
    public CompletableFuture<ResponseEntity<AlertIntegration.AlertResult>> dispatchToIntegration(
            @Parameter(description = "ID of the notification to dispatch")
            @PathVariable Long notificationId,
            @Parameter(description = "Name of the integration (e.g., 'pagerduty', 'slack')")
            @PathVariable String integrationName) {

        return notificationRepository.findById(notificationId)
                .map(notification -> alertDispatcher.dispatchToIntegration(integrationName, notification)
                        .thenApply(ResponseEntity::ok))
                .orElse(CompletableFuture.completedFuture(ResponseEntity.notFound().build()));
    }

    @Operation(
            summary = "Resolve alert on all integrations",
            description = "Sends resolve signal to all enabled integrations for a notification.")
    @ApiResponse(responseCode = "200", description = "Resolve dispatched")
    @ApiResponse(responseCode = "404", description = "Notification not found")
    @PostMapping("/resolve/{notificationId}")
    @PreAuthorize(Roles.HAS_OPERATOR)
    public CompletableFuture<ResponseEntity<AlertDispatcher.DispatchResult>> resolveNotification(
            @Parameter(description = "ID of the notification to resolve")
            @PathVariable Long notificationId) {

        return notificationRepository.findById(notificationId)
                .map(notification -> alertDispatcher.dispatchResolve(notification)
                        .thenApply(ResponseEntity::ok))
                .orElse(CompletableFuture.completedFuture(ResponseEntity.notFound().build()));
    }
}
