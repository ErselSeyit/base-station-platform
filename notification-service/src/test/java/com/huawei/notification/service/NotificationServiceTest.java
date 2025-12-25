package com.huawei.notification.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.huawei.notification.model.Notification;
import com.huawei.notification.model.NotificationStatus;
import com.huawei.notification.model.NotificationType;
import com.huawei.notification.repository.NotificationRepository;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository repository;

    @InjectMocks
    private NotificationService service;

    @Captor
    private ArgumentCaptor<Notification> notificationCaptor;

    @Test
    @SuppressWarnings("null")
    void testCreateNotification_Success() {
        final Notification notification = new Notification();
        notification.setId(1L);
        notification.setStationId(1L);
        notification.setMessage("Test notification");
        notification.setType(NotificationType.ALERT);
        notification.setStatus(NotificationStatus.PENDING);

        when(repository.save(any(Notification.class))).thenAnswer(invocation -> {
            Notification arg = invocation.getArgument(0, Notification.class);
            return arg;
        });

        Notification result = service.createNotification(1L, "Test notification", NotificationType.ALERT);

        assertNotNull(result);
        assertEquals(1L, result.getStationId());
        assertEquals(NotificationType.ALERT, result.getType());
        verify(repository, times(1)).save(notificationCaptor.capture());
        assertNotNull(notificationCaptor.getValue());
    }

    @Test
    void testGetNotificationsByStation() {
        final Notification notification = new Notification();
        notification.setId(1L);
        notification.setStationId(1L);
        notification.setMessage("Test notification");
        notification.setType(NotificationType.ALERT);
        notification.setStatus(NotificationStatus.PENDING);

        final Notification notification2 = new Notification();
        notification2.setId(2L);
        notification2.setStationId(1L);
        notification2.setMessage("Another notification");

        when(repository.findByStationId(1L)).thenReturn(Arrays.asList(notification, notification2));

        List<Notification> result = service.getNotificationsByStation(1L);

        assertEquals(2, result.size());
        verify(repository, times(1)).findByStationId(1L);
    }

    @Test
    void testGetNotificationsByStatus() {
        final Notification notification = new Notification();
        notification.setId(1L);
        notification.setStationId(1L);
        notification.setMessage("Test notification");
        notification.setType(NotificationType.ALERT);
        notification.setStatus(NotificationStatus.PENDING);

        when(repository.findByStatus(NotificationStatus.PENDING))
                .thenReturn(Arrays.asList(notification));

        List<Notification> result = service.getNotificationsByStatus(NotificationStatus.PENDING);

        assertEquals(1, result.size());
        assertEquals(NotificationStatus.PENDING, result.get(0).getStatus());
    }

    @Test
    @SuppressWarnings("null")
    void testSendNotification_Success() {
        final Notification notification = new Notification();
        notification.setId(1L);
        notification.setStationId(1L);
        notification.setMessage("Test notification");
        notification.setType(NotificationType.ALERT);
        notification.setStatus(NotificationStatus.PENDING);

        when(repository.findById(1L)).thenReturn(Optional.of(notification));
        when(repository.save(any(Notification.class))).thenAnswer(invocation -> notification);

        assertDoesNotThrow(() -> service.sendNotification(1L));
        verify(repository, times(1)).save(notificationCaptor.capture());
        assertNotNull(notificationCaptor.getValue());
    }

    @Test
    void testSendNotification_NotFound() {
        when(repository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> service.sendNotification(1L));
    }
}
