package io.github.erselseyit.basestation.notification.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import io.github.erselseyit.basestation.notification.exception.NotificationNotFoundException;
import io.github.erselseyit.basestation.notification.model.Notification;
import io.github.erselseyit.basestation.notification.model.NotificationStatus;
import io.github.erselseyit.basestation.notification.model.NotificationType;
import io.github.erselseyit.basestation.notification.repository.NotificationRepository;

/**
 * Extended unit tests for NotificationService.
 *
 * Covers methods not tested in the existing NotificationServiceTest:
 * createNotificationWithProblemId, resolveByProblemId, sendNotification edge cases,
 * deleteAllUnread, getRecentNotifications, getCounts caching, and processPendingNotifications.
 */
@ExtendWith(MockitoExtension.class)
class NotificationServiceExtendedTest {

    @Mock
    private NotificationRepository repository;

    @Mock
    private AsyncNotificationExecutor asyncExecutor;

    @InjectMocks
    private NotificationService service;

    @Captor
    private ArgumentCaptor<Notification> notificationCaptor;

    // --- createNotificationWithProblemId tests ---

    @Test
    @SuppressWarnings("null")
    void testCreateNotificationWithProblemId_SetsProblemId() {
        // Given
        String problemId = "PROB-1-CPU-12345";
        Notification saved = new Notification(1L, "High CPU", NotificationType.ERROR);
        saved.setId(1L);
        saved.setProblemId(problemId);

        when(repository.save(notificationCaptor.capture())).thenReturn(saved);

        // When
        Notification result = service.createNotificationWithProblemId(1L, "High CPU", NotificationType.ERROR, problemId);

        // Then
        assertNotNull(result);
        assertEquals(problemId, result.getProblemId());

        Notification captured = notificationCaptor.getValue();
        assertEquals(problemId, captured.getProblemId());
        assertEquals(1L, captured.getStationId());
        assertEquals("High CPU", captured.getMessage());
        assertEquals(NotificationType.ERROR, captured.getType());
    }

    // --- resolveByProblemId tests ---

    @Test
    @SuppressWarnings("null")
    void testResolveByProblemId_UpdatesStatusToResolved() {
        // Given
        String problemId = "PROB-1-CPU-12345";
        Notification notification = new Notification(1L, "Alert message", NotificationType.ALERT);
        notification.setId(1L);
        notification.setStatus(NotificationStatus.UNREAD);

        when(repository.findByProblemId(problemId)).thenReturn(List.of(notification));
        when(repository.save(any(Notification.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        int resolved = service.resolveByProblemId(problemId);

        // Then
        assertEquals(1, resolved);
        verify(repository).save(notificationCaptor.capture());
        assertEquals(NotificationStatus.RESOLVED, notificationCaptor.getValue().getStatus());
    }

    @Test
    @SuppressWarnings("null")
    void testResolveByProblemId_SetsResolvedAtTimestamp() {
        // Given
        String problemId = "PROB-2-TEMP-67890";
        Notification notification = new Notification(2L, "Temperature alert", NotificationType.WARNING);
        notification.setId(2L);
        notification.setStatus(NotificationStatus.UNREAD);

        when(repository.findByProblemId(problemId)).thenReturn(List.of(notification));
        when(repository.save(any(Notification.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        service.resolveByProblemId(problemId);

        // Then
        verify(repository).save(notificationCaptor.capture());
        assertNotNull(notificationCaptor.getValue().getResolvedAt(),
                "resolvedAt should be populated when a notification is resolved");
    }

    @Test
    void testResolveByProblemId_NoProblemId_ThrowsException() {
        // Given null problemId
        // When
        int resolvedNull = service.resolveByProblemId(null);
        int resolvedBlank = service.resolveByProblemId("   ");

        // Then - should return 0 without calling repository
        assertEquals(0, resolvedNull);
        assertEquals(0, resolvedBlank);
        verify(repository, never()).findByProblemId(any());
    }

    @Test
    void testResolveByProblemId_NoMatchingNotifications_ReturnsZero() {
        // Given
        String problemId = "PROB-NONEXISTENT";
        when(repository.findByProblemId(problemId)).thenReturn(Collections.emptyList());

        // When
        int resolved = service.resolveByProblemId(problemId);

        // Then
        assertEquals(0, resolved);
        verify(repository, never()).save(any(Notification.class));
    }

    // --- sendNotification tests ---

    @Test
    void testSendNotification_NotFoundThrowsException() {
        // Given
        when(repository.findById(999L)).thenReturn(Optional.empty());

        // When & Then
        assertThrows(NotificationNotFoundException.class,
                () -> service.sendNotification(999L));
    }

    @Test
    @SuppressWarnings("null")
    void testSendNotification_NotPendingStatus_SkipsSend() {
        // Given - notification already in SENT status
        Notification notification = new Notification(1L, "Already sent", NotificationType.ALERT);
        notification.setId(1L);
        notification.setStatus(NotificationStatus.SENT);

        when(repository.findById(1L)).thenReturn(Optional.of(notification));

        // When
        service.sendNotification(1L);

        // Then - should not save (skip due to idempotency check)
        verify(repository, never()).save(any(Notification.class));
    }

    @Test
    @SuppressWarnings("null")
    void testSendNotification_PendingStatus_MarksSent() {
        // Given
        Notification notification = new Notification(1L, "Pending notification", NotificationType.ALERT);
        notification.setId(1L);
        notification.setStatus(NotificationStatus.PENDING);

        when(repository.findById(1L)).thenReturn(Optional.of(notification));
        when(repository.save(any(Notification.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        service.sendNotification(1L);

        // Then
        verify(repository).save(notificationCaptor.capture());
        Notification saved = notificationCaptor.getValue();
        assertEquals(NotificationStatus.SENT, saved.getStatus());
        assertNotNull(saved.getSentAt(), "sentAt should be populated after successful send");
    }

    // --- deleteAllUnread tests ---

    @Test
    void testDeleteAllUnread_CallsBulkDelete() {
        // Given
        when(repository.deleteByStatusBulk(NotificationStatus.UNREAD)).thenReturn(5);

        // When
        int deleted = service.deleteAllUnread();

        // Then
        assertEquals(5, deleted);
        verify(repository).deleteByStatusBulk(NotificationStatus.UNREAD);
    }

    // --- getRecentNotifications tests ---

    @Test
    @SuppressWarnings("null")
    void testGetRecentNotifications_ReturnsPagedResults() {
        // Given
        Notification n1 = new Notification(1L, "Recent alert", NotificationType.ALERT);
        n1.setId(1L);
        n1.setStatus(NotificationStatus.UNREAD);
        Notification n2 = new Notification(2L, "Resolved alert", NotificationType.WARNING);
        n2.setId(2L);
        n2.setStatus(NotificationStatus.RESOLVED);

        Page<Notification> page = new PageImpl<>(List.of(n1, n2));
        when(repository.findByStatusIn(any(), any(Pageable.class))).thenReturn(page);

        // When
        List<Notification> recent = service.getRecentNotifications();

        // Then
        assertEquals(2, recent.size());
        verify(repository).findByStatusIn(any(), any(Pageable.class));
    }

    // --- getCounts tests ---

    @Test
    void testGetCounts_CachesResults() {
        // Given
        when(repository.countByStatus(NotificationStatus.UNREAD)).thenReturn(10L);
        when(repository.countByTypeAndStatus(NotificationType.ALERT, NotificationStatus.UNREAD)).thenReturn(5L);
        when(repository.countByTypeAndStatus(NotificationType.WARNING, NotificationStatus.UNREAD)).thenReturn(3L);

        // When - first call fetches from repository
        NotificationService.NotificationCounts counts1 = service.getCounts();
        // Second call should use cache (within 10s TTL)
        NotificationService.NotificationCounts counts2 = service.getCounts();

        // Then
        assertEquals(10L, counts1.total());
        assertEquals(10L, counts1.unread());
        assertEquals(5L, counts1.alerts());
        assertEquals(3L, counts1.warnings());

        // Verify same values from cache
        assertEquals(counts1.total(), counts2.total());
        assertEquals(counts1.unread(), counts2.unread());

        // countByStatus is called twice in getCounts (total and unread both use UNREAD),
        // but should only happen once (first call), not on second call
        verify(repository, times(2)).countByStatus(NotificationStatus.UNREAD);
        verify(repository, times(1)).countByTypeAndStatus(NotificationType.ALERT, NotificationStatus.UNREAD);
        verify(repository, times(1)).countByTypeAndStatus(NotificationType.WARNING, NotificationStatus.UNREAD);
    }

    // --- processPendingNotifications tests ---

    @Test
    void testProcessPendingNotifications_SendsAllPending() {
        // Given
        Notification n1 = new Notification(1L, "Pending 1", NotificationType.ALERT);
        n1.setId(1L);
        n1.setStatus(NotificationStatus.PENDING);
        Notification n2 = new Notification(2L, "Pending 2", NotificationType.WARNING);
        n2.setId(2L);
        n2.setStatus(NotificationStatus.PENDING);

        when(repository.findByStatus(NotificationStatus.PENDING)).thenReturn(List.of(n1, n2));
        when(asyncExecutor.sendAsync(any(), any())).thenReturn(CompletableFuture.completedFuture(null));

        // When
        CompletableFuture<Void> future = service.processPendingNotifications();

        // Then
        assertNotNull(future);
        verify(asyncExecutor, times(2)).sendAsync(any(), any());
        verify(asyncExecutor).sendAsync(ArgumentMatchers.eq(1L), any());
        verify(asyncExecutor).sendAsync(ArgumentMatchers.eq(2L), any());
    }
}
