package com.huawei.notification.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.mockito.ArgumentMatchers;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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

    @Test
    @SuppressWarnings("null")
    void createNotification_savesAndReturnsNotification() {
        Notification notification = new Notification();
        notification.setId(1L);
        notification.setStationId(1L);
        notification.setMessage("Test notification");
        notification.setType(NotificationType.ALERT);
        notification.setStatus(NotificationStatus.PENDING);

        when(repository.save(ArgumentMatchers.<Notification>any())).thenReturn(notification);

        Notification result = service.createNotification(1L, "Test notification", NotificationType.ALERT);

        assertNotNull(result);
        assertEquals(1L, result.getStationId());
        assertEquals(NotificationType.ALERT, result.getType());
        verify(repository).save(ArgumentMatchers.<Notification>any());
    }

    @Test
    void getNotificationsByStation_returnsAllForStation() {
        Notification notification1 = new Notification();
        notification1.setId(1L);
        notification1.setStationId(1L);
        notification1.setMessage("Test notification");
        notification1.setType(NotificationType.ALERT);
        notification1.setStatus(NotificationStatus.PENDING);

        Notification notification2 = new Notification();
        notification2.setId(2L);
        notification2.setStationId(1L);
        notification2.setMessage("Another notification");

        when(repository.findByStationId(1L)).thenReturn(List.of(notification1, notification2));

        List<Notification> result = service.getNotificationsByStation(1L);

        assertEquals(2, result.size());
    }

    @Test
    void getNotificationsByStatus_filtersCorrectly() {
        Notification notification = new Notification();
        notification.setId(1L);
        notification.setStationId(1L);
        notification.setMessage("Test notification");
        notification.setType(NotificationType.ALERT);
        notification.setStatus(NotificationStatus.PENDING);

        when(repository.findByStatus(NotificationStatus.PENDING)).thenReturn(List.of(notification));

        List<Notification> result = service.getNotificationsByStatus(NotificationStatus.PENDING);

        assertEquals(1, result.size());
        assertEquals(NotificationStatus.PENDING, result.get(0).getStatus());
    }

    @Test
    @SuppressWarnings("null")
    void sendNotification_updatesStatus() {
        Notification notification = new Notification();
        notification.setId(1L);
        notification.setStationId(1L);
        notification.setMessage("Test notification");
        notification.setType(NotificationType.ALERT);
        notification.setStatus(NotificationStatus.PENDING);

        when(repository.findById(1L)).thenReturn(Optional.of(notification));
        when(repository.save(ArgumentMatchers.<Notification>any())).thenReturn(notification);

        assertDoesNotThrow(() -> service.sendNotification(1L));
        verify(repository).save(ArgumentMatchers.<Notification>any());
    }

    @Test
    void sendNotification_throwsWhenNotFound() {
        when(repository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> service.sendNotification(1L));
    }
}
