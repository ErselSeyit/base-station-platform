package com.huawei.notification.listener;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.huawei.common.dto.AlertEvent;
import com.huawei.notification.exception.NotificationException;
import com.huawei.notification.model.Notification;
import com.huawei.notification.model.NotificationType;
import com.huawei.notification.service.NotificationService;

/**
 * Unit tests for AlertEventListener.
 *
 * Tests the RabbitMQ listener that converts AlertEvents into notifications,
 * including severity mapping, problemId linking, and error handling.
 */
@ExtendWith(MockitoExtension.class)
class AlertEventListenerTest {

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private AlertEventListener listener;

    @Captor
    private ArgumentCaptor<String> messageCaptor;

    @Captor
    private ArgumentCaptor<NotificationType> typeCaptor;

    @Test
    void testHandleAlertEvent_CriticalSeverity_MapsToErrorType() {
        // Given
        AlertEvent event = createBaseAlertEventBuilder()
                .severity("CRITICAL")
                .build();

        when(notificationService.createNotification(anyLong(), anyString(), any(NotificationType.class)))
                .thenReturn(new Notification());

        // When
        listener.handleAlertEvent(event);

        // Then
        verify(notificationService).createNotification(eq(1L), anyString(), eq(NotificationType.ERROR));
    }

    @Test
    void testHandleAlertEvent_WarningSeverity_MapsToWarningType() {
        // Given
        AlertEvent event = createBaseAlertEventBuilder()
                .severity("WARNING")
                .build();

        when(notificationService.createNotification(anyLong(), anyString(), any(NotificationType.class)))
                .thenReturn(new Notification());

        // When
        listener.handleAlertEvent(event);

        // Then
        verify(notificationService).createNotification(eq(1L), anyString(), eq(NotificationType.WARNING));
    }

    @Test
    void testHandleAlertEvent_InfoSeverity_MapsToInfoType() {
        // Given
        AlertEvent event = createBaseAlertEventBuilder()
                .severity("INFO")
                .build();

        when(notificationService.createNotification(anyLong(), anyString(), any(NotificationType.class)))
                .thenReturn(new Notification());

        // When
        listener.handleAlertEvent(event);

        // Then
        verify(notificationService).createNotification(eq(1L), anyString(), eq(NotificationType.INFO));
    }

    @Test
    void testHandleAlertEvent_WithProblemId_UsesCreateWithProblemId() {
        // Given
        String problemId = "PROB-1-CPU-12345";
        AlertEvent event = createBaseAlertEventBuilder()
                .severity("CRITICAL")
                .problemId(problemId)
                .build();

        when(notificationService.createNotificationWithProblemId(
                anyLong(), anyString(), any(NotificationType.class), anyString()))
                .thenReturn(new Notification());

        // When
        listener.handleAlertEvent(event);

        // Then
        verify(notificationService).createNotificationWithProblemId(
                eq(1L), anyString(), eq(NotificationType.ERROR), eq(problemId));
        verify(notificationService, never()).createNotification(anyLong(), anyString(), any(NotificationType.class));
    }

    @Test
    void testHandleAlertEvent_WithoutProblemId_UsesCreateNotification() {
        // Given
        AlertEvent event = createBaseAlertEventBuilder()
                .severity("WARNING")
                .problemId(null)
                .build();

        when(notificationService.createNotification(anyLong(), anyString(), any(NotificationType.class)))
                .thenReturn(new Notification());

        // When
        listener.handleAlertEvent(event);

        // Then
        verify(notificationService).createNotification(eq(1L), anyString(), eq(NotificationType.WARNING));
        verify(notificationService, never()).createNotificationWithProblemId(
                anyLong(), anyString(), any(NotificationType.class), anyString());
    }

    @Test
    void testHandleAlertEvent_MessageContainsAlertDetails() {
        // Given
        AlertEvent event = AlertEvent.builder()
                .alertRuleId("rule-1")
                .alertRuleName("High CPU Alert")
                .stationId(42L)
                .stationName("Station-42")
                .metricType("cpu_usage")
                .metricValue(95.5)
                .threshold(80.0)
                .severity("CRITICAL")
                .message("CPU usage exceeded threshold")
                .build();

        when(notificationService.createNotification(anyLong(), messageCaptor.capture(), any(NotificationType.class)))
                .thenReturn(new Notification());

        // When
        listener.handleAlertEvent(event);

        // Then
        String capturedMessage = messageCaptor.getValue();
        assertTrue(capturedMessage.contains("High CPU Alert"),
                "Message should contain alert rule name");
        assertTrue(capturedMessage.contains("CPU usage exceeded threshold"),
                "Message should contain alert message/description");
        assertTrue(capturedMessage.contains("Station-42"),
                "Message should contain station name");
        assertTrue(capturedMessage.contains("95.50"),
                "Message should contain metric value");
        assertTrue(capturedMessage.contains("80.00"),
                "Message should contain threshold value");
    }

    @Test
    void testHandleAlertEvent_NullEvent_HandlesGracefully() {
        // Given a null event
        // When & Then - should throw NotificationException wrapping the NullPointerException
        assertThrows(NullPointerException.class, () -> listener.handleAlertEvent(null));
    }

    @Test
    void testHandleAlertEvent_ExceptionInService_ThrowsNotificationException() {
        // Given
        AlertEvent event = createBaseAlertEventBuilder()
                .severity("CRITICAL")
                .build();

        when(notificationService.createNotification(anyLong(), anyString(), any(NotificationType.class)))
                .thenThrow(new RuntimeException("Database connection lost"));

        // When & Then
        NotificationException thrown = assertThrows(NotificationException.class,
                () -> listener.handleAlertEvent(event));
        assertTrue(thrown.getMessage().contains("Failed to process alert event"),
                "Exception message should indicate alert processing failure");
        assertTrue(thrown.getMessage().contains("1"),
                "Exception message should contain the station ID");
    }

    /**
     * Creates a base AlertEvent builder with all required fields populated.
     * Individual tests can override specific fields as needed.
     */
    private AlertEvent.Builder createBaseAlertEventBuilder() {
        return AlertEvent.builder()
                .alertRuleId("rule-1")
                .alertRuleName("Test Alert")
                .stationId(1L)
                .stationName("Test Station")
                .metricType("cpu_usage")
                .metricValue(90.0)
                .threshold(80.0)
                .message("Metric exceeded threshold");
    }
}
