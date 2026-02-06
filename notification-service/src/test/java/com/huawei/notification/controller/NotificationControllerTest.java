package com.huawei.notification.controller;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huawei.notification.dto.NotificationRequest;
import com.huawei.notification.exception.NotificationException;
import com.huawei.notification.model.Notification;
import com.huawei.notification.model.NotificationStatus;
import com.huawei.notification.model.NotificationType;
import com.huawei.notification.service.NotificationService;

/**
 * Unit tests for NotificationController.
 *
 * Tests all REST endpoints for creating, sending, and retrieving notifications.
 * Security is disabled via addFilters=false to focus on controller logic.
 */
@WebMvcTest(NotificationController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("NotificationController Tests")
class NotificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private NotificationService notificationService;

    @Test
    @DisplayName("POST /api/v1/notifications - Should create notification successfully")
    void createNotification_ValidRequest_ReturnsCreated() throws Exception {
        // Given
        Long stationId = 1L;
        String message = "High CPU usage detected";
        NotificationType type = NotificationType.ALERT;

        NotificationRequest request = new NotificationRequest();
        request.setStationId(stationId);
        request.setMessage(message);
        request.setType(type);

        Notification notification = createNotification(1L, stationId, message, type, NotificationStatus.PENDING);
        when(notificationService.createNotification(stationId, message, type)).thenReturn(notification);

        // When & Then
        String requestJson = Objects.requireNonNull(objectMapper.writeValueAsString(request));
        mockMvc.perform(post("/api/v1/notifications")
                .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                .content(requestJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.stationId").value(stationId))
                .andExpect(jsonPath("$.message").value(message))
                .andExpect(jsonPath("$.type").value(type.toString()))
                .andExpect(jsonPath("$.status").value(NotificationStatus.PENDING.toString()));

        verify(notificationService).createNotification(stationId, message, type);
    }

    @Test
    @DisplayName("POST /api/v1/notifications/{id}/send - Should send notification successfully")
    void sendNotification_ValidId_ReturnsSuccess() throws Exception {
        // Given
        Long notificationId = 1L;

        // When & Then
        mockMvc.perform(post("/api/v1/notifications/{id}/send", notificationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("sent"))
                .andExpect(jsonPath("$.message").value("Notification sent successfully"));

        verify(notificationService).sendNotification(notificationId);
    }

    @Test
    @DisplayName("POST /api/v1/notifications/{id}/send - Should return error when send fails")
    void sendNotification_ServiceError_ReturnsInternalServerError() throws Exception {
        // Given
        Long notificationId = 1L;
        String errorMessage = "Email service unavailable";
        doThrow(new NotificationException(errorMessage))
                .when(notificationService).sendNotification(anyLong());

        // When & Then
        mockMvc.perform(post("/api/v1/notifications/{id}/send", notificationId))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.message").value(errorMessage));

        verify(notificationService).sendNotification(notificationId);
    }

    @Test
    @DisplayName("POST /api/v1/notifications/process-pending - Should start processing pending notifications")
    void processPendingNotifications_ValidRequest_ReturnsAccepted() throws Exception {
        // Given
        when(notificationService.processPendingNotifications())
                .thenReturn(CompletableFuture.completedFuture(null));

        // When & Then
        mockMvc.perform(post("/api/v1/notifications/process-pending"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("processing"))
                .andExpect(jsonPath("$.message").value("Processing pending notifications in background"));

        verify(notificationService).processPendingNotifications();
    }

    @Test
    @DisplayName("GET /api/v1/notifications/station/{stationId} - Should return notifications for station")
    void getNotificationsByStation_ValidStationId_ReturnsNotifications() throws Exception {
        // Given
        Long stationId = 1L;
        List<Notification> notifications = Arrays.asList(
                createNotification(1L, stationId, "Alert 1", NotificationType.ALERT, NotificationStatus.SENT),
                createNotification(2L, stationId, "Info 1", NotificationType.INFO, NotificationStatus.PENDING),
                createNotification(3L, stationId, "Warning 1", NotificationType.WARNING, NotificationStatus.SENT)
        );
        when(notificationService.getNotificationsByStation(stationId)).thenReturn(notifications);

        // When & Then
        mockMvc.perform(get("/api/v1/notifications/station/{stationId}", stationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].stationId").value(stationId))
                .andExpect(jsonPath("$[0].message").value("Alert 1"))
                .andExpect(jsonPath("$[1].message").value("Info 1"))
                .andExpect(jsonPath("$[2].message").value("Warning 1"));

        verify(notificationService).getNotificationsByStation(stationId);
    }

    @Test
    @DisplayName("GET /api/v1/notifications/station/{stationId} - Should return empty list for station with no notifications")
    void getNotificationsByStation_NoNotifications_ReturnsEmptyList() throws Exception {
        // Given
        Long stationId = 999L;
        when(notificationService.getNotificationsByStation(stationId)).thenReturn(Collections.emptyList());

        // When & Then
        mockMvc.perform(get("/api/v1/notifications/station/{stationId}", stationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));

        verify(notificationService).getNotificationsByStation(stationId);
    }

    @Test
    @DisplayName("GET /api/v1/notifications - Should return all notifications when no status provided")
    void getNotifications_NoStatus_ReturnsAllNotifications() throws Exception {
        // Given
        List<Notification> notifications = Arrays.asList(
                createNotification(1L, 1L, "Alert 1", NotificationType.ALERT, NotificationStatus.SENT),
                createNotification(2L, 2L, "Info 1", NotificationType.INFO, NotificationStatus.PENDING)
        );
        when(notificationService.getAllNotifications()).thenReturn(notifications);

        // When & Then
        mockMvc.perform(get("/api/v1/notifications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(1L))
                .andExpect(jsonPath("$[1].id").value(2L));

        verify(notificationService).getAllNotifications();
    }

    @Test
    @DisplayName("GET /api/v1/notifications?status=PENDING - Should return notifications filtered by status")
    void getNotifications_WithStatus_ReturnsFilteredNotifications() throws Exception {
        // Given
        NotificationStatus status = NotificationStatus.PENDING;
        List<Notification> notifications = Arrays.asList(
                createNotification(1L, 1L, "Pending 1", NotificationType.INFO, status),
                createNotification(2L, 2L, "Pending 2", NotificationType.ALERT, status)
        );
        when(notificationService.getNotificationsByStatus(status)).thenReturn(notifications);

        // When & Then
        mockMvc.perform(get("/api/v1/notifications")
                .param("status", status.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].status").value(status.toString()))
                .andExpect(jsonPath("$[1].status").value(status.toString()));

        verify(notificationService).getNotificationsByStatus(status);
    }

    @Test
    @DisplayName("GET /api/v1/notifications?status=SENT - Should return sent notifications")
    void getNotifications_StatusSent_ReturnsSentNotifications() throws Exception {
        // Given
        NotificationStatus status = NotificationStatus.SENT;
        List<Notification> notifications = Arrays.asList(
                createNotification(1L, 1L, "Sent 1", NotificationType.ALERT, status)
        );
        when(notificationService.getNotificationsByStatus(status)).thenReturn(notifications);

        // When & Then
        mockMvc.perform(get("/api/v1/notifications")
                .param("status", status.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].status").value(status.toString()));

        verify(notificationService).getNotificationsByStatus(status);
    }

    @Test
    @DisplayName("GET /api/v1/notifications?status=FAILED - Should return failed notifications")
    void getNotifications_StatusFailed_ReturnsFailedNotifications() throws Exception {
        // Given
        NotificationStatus status = NotificationStatus.FAILED;
        List<Notification> notifications = Arrays.asList(
                createNotification(1L, 1L, "Failed 1", NotificationType.ALERT, status),
                createNotification(2L, 2L, "Failed 2", NotificationType.WARNING, status)
        );
        when(notificationService.getNotificationsByStatus(status)).thenReturn(notifications);

        // When & Then
        mockMvc.perform(get("/api/v1/notifications")
                .param("status", status.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].status").value(status.toString()))
                .andExpect(jsonPath("$[1].status").value(status.toString()));

        verify(notificationService).getNotificationsByStatus(status);
    }

    private Notification createNotification(Long id, Long stationId, String message,
                                          NotificationType type, NotificationStatus status) {
        Notification notification = new Notification();
        notification.setId(id);
        notification.setStationId(stationId);
        notification.setMessage(message);
        notification.setType(type);
        notification.setStatus(status);
        notification.setCreatedAt(LocalDateTime.now());
        return notification;
    }
}
