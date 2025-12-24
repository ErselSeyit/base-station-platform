package com.huawei.notification.service;

import com.huawei.notification.model.Notification;
import com.huawei.notification.model.NotificationStatus;
import com.huawei.notification.model.NotificationType;
import com.huawei.notification.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository repository;

    @InjectMocks
    private NotificationService service;

    private Notification testNotification;

    @BeforeEach
    void setUp() {
        testNotification = new Notification();
        testNotification.setId(1L);
        testNotification.setStationId(1L);
        testNotification.setMessage("Test notification");
        testNotification.setType(NotificationType.ALERT);
        testNotification.setStatus(NotificationStatus.PENDING);
    }

    @Test
    void testCreateNotification_Success() {
        when(repository.save(any(Notification.class))).thenReturn(testNotification);

        Notification result = service.createNotification(1L, "Test notification", NotificationType.ALERT);

        assertNotNull(result);
        assertEquals(1L, result.getStationId());
        assertEquals(NotificationType.ALERT, result.getType());
        verify(repository, times(1)).save(any(Notification.class));
    }

    @Test
    void testGetNotificationsByStation() {
        Notification notification2 = new Notification();
        notification2.setId(2L);
        notification2.setStationId(1L);
        notification2.setMessage("Another notification");

        when(repository.findByStationId(1L)).thenReturn(Arrays.asList(testNotification, notification2));

        List<Notification> result = service.getNotificationsByStation(1L);

        assertEquals(2, result.size());
        verify(repository, times(1)).findByStationId(1L);
    }

    @Test
    void testGetNotificationsByStatus() {
        when(repository.findByStatus(NotificationStatus.PENDING))
                .thenReturn(Arrays.asList(testNotification));

        List<Notification> result = service.getNotificationsByStatus(NotificationStatus.PENDING);

        assertEquals(1, result.size());
        assertEquals(NotificationStatus.PENDING, result.get(0).getStatus());
    }

    @Test
    void testSendNotification_Success() {
        when(repository.findById(1L)).thenReturn(Optional.of(testNotification));
        when(repository.save(any(Notification.class))).thenReturn(testNotification);

        assertDoesNotThrow(() -> service.sendNotification(1L));
        verify(repository, times(1)).save(any(Notification.class));
    }

    @Test
    void testSendNotification_NotFound() {
        when(repository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> service.sendNotification(1L));
    }
}

