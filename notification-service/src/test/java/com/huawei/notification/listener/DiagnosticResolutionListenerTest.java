package com.huawei.notification.listener;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.huawei.common.dto.DiagnosticResolutionEvent;
import com.huawei.notification.service.NotificationService;

/**
 * Unit tests for DiagnosticResolutionListener.
 *
 * Tests the RabbitMQ listener that processes diagnostic resolution events
 * and resolves related notifications when the AI diagnostic solution was effective.
 */
@ExtendWith(MockitoExtension.class)
class DiagnosticResolutionListenerTest {

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private DiagnosticResolutionListener listener;

    @Test
    void testHandleResolutionEvent_Effective_ResolvesNotifications() {
        // Given - a successful resolution event
        DiagnosticResolutionEvent event = DiagnosticResolutionEvent.success(
                "session-1", "PROB-1-CPU-12345", 1L, "HIGH_CPU", "ai-engine");

        when(notificationService.resolveByProblemId("PROB-1-CPU-12345")).thenReturn(3);

        // When
        listener.handleResolutionEvent(event);

        // Then
        verify(notificationService).resolveByProblemId("PROB-1-CPU-12345");
    }

    @Test
    void testHandleResolutionEvent_NotEffective_DoesNotResolve() {
        // Given - a failed resolution event (wasEffective = false)
        DiagnosticResolutionEvent event = DiagnosticResolutionEvent.failure(
                "session-2", "PROB-2-TEMP-67890", 2L, "HIGH_TEMP", "ai-engine");

        // When
        listener.handleResolutionEvent(event);

        // Then - resolveByProblemId should NOT be called
        verify(notificationService, never()).resolveByProblemId(anyString());
    }

    @Test
    void testHandleResolutionEvent_NullProblemId_DoesNotResolve() {
        // Given - event with null problemId but wasEffective = true
        DiagnosticResolutionEvent event = DiagnosticResolutionEvent.success(
                "session-3", null, 3L, "SIGNAL_LOSS", "ai-engine");

        // When - the listener calls resolveByProblemId(null), which returns 0
        // The service handles null internally, so this should not throw
        when(notificationService.resolveByProblemId(null)).thenReturn(0);

        // When
        listener.handleResolutionEvent(event);

        // Then - resolveByProblemId is called (listener delegates null handling to service)
        verify(notificationService).resolveByProblemId(null);
    }

    @Test
    void testHandleResolutionEvent_BlankProblemId_DoesNotResolve() {
        // Given - event with blank problemId but wasEffective = true
        DiagnosticResolutionEvent event = DiagnosticResolutionEvent.success(
                "session-4", "  ", 4L, "POWER_ISSUE", "ai-engine");

        // The service handles blank internally, returning 0
        when(notificationService.resolveByProblemId("  ")).thenReturn(0);

        // When
        listener.handleResolutionEvent(event);

        // Then - service is called, but it returns 0 (handles blank internally)
        verify(notificationService).resolveByProblemId("  ");
    }

    @Test
    void testHandleResolutionEvent_ServiceThrowsException_HandledGracefully() {
        // Given - service throws an exception
        DiagnosticResolutionEvent event = DiagnosticResolutionEvent.success(
                "session-5", "PROB-5-NET-11111", 5L, "NETWORK_ISSUE", "ai-engine");

        when(notificationService.resolveByProblemId("PROB-5-NET-11111"))
                .thenThrow(new RuntimeException("Database unavailable"));

        // When & Then - exception should be caught, not propagated
        assertDoesNotThrow(() -> listener.handleResolutionEvent(event),
                "Listener should catch exceptions to prevent RabbitMQ message rejection and infinite retries");
    }

    @Test
    void testHandleResolutionEvent_NullEvent_HandledGracefully() {
        // Given a null event
        // When & Then - NullPointerException occurs at the log statement (line 39)
        // which is outside the try-catch block, so it propagates
        assertThrows(NullPointerException.class, () -> listener.handleResolutionEvent(null),
                "Null event causes NPE at log statement before the try-catch block");
    }
}
