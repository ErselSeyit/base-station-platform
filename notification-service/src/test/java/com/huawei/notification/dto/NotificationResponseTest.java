package com.huawei.notification.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import com.huawei.notification.model.Notification;
import com.huawei.notification.model.NotificationStatus;
import com.huawei.notification.model.NotificationType;

/**
 * Unit tests for NotificationResponse DTO.
 *
 * Tests the fromEntity factory method to ensure all fields
 * are correctly mapped from the Notification entity to the response DTO.
 */
@ExtendWith(MockitoExtension.class)
class NotificationResponseTest {

    @Test
    void testFromEntity_MapsAllFields() {
        // Given
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime sentTime = now.plusMinutes(1);
        LocalDateTime resolvedTime = now.plusMinutes(5);

        Notification notification = new Notification(1L, "High CPU alert", NotificationType.ERROR);
        notification.setId(42L);
        notification.setStatus(NotificationStatus.RESOLVED);
        notification.setCreatedAt(now);
        notification.setUpdatedAt(now.plusSeconds(30));
        notification.setSentAt(sentTime);
        notification.setProblemId("PROB-1-CPU-12345");
        notification.setResolvedAt(resolvedTime);

        // When
        NotificationResponse response = NotificationResponse.fromEntity(notification);

        // Then
        assertEquals(42L, response.getId());
        assertEquals(1L, response.getStationId());
        assertEquals("High CPU alert", response.getMessage());
        assertEquals(NotificationType.ERROR, response.getType());
        assertEquals(NotificationStatus.RESOLVED, response.getStatus());
        assertEquals(now, response.getCreatedAt());
        assertEquals(sentTime, response.getSentAt());
        assertEquals("PROB-1-CPU-12345", response.getProblemId());
        assertEquals(resolvedTime, response.getResolvedAt());
    }

    @Test
    void testFromEntity_NullOptionalFields_HandledGracefully() {
        // Given - notification with only required fields, optional fields are null
        Notification notification = new Notification(5L, "Simple alert", NotificationType.ALERT);
        notification.setId(10L);
        notification.setStatus(NotificationStatus.UNREAD);
        notification.setCreatedAt(LocalDateTime.now());
        // sentAt, resolvedAt, problemId are intentionally NOT set (remain null)

        // When
        NotificationResponse response = NotificationResponse.fromEntity(notification);

        // Then - required fields present
        assertNotNull(response.getId());
        assertNotNull(response.getStationId());
        assertNotNull(response.getMessage());
        assertNotNull(response.getType());
        assertNotNull(response.getStatus());

        // Optional fields should be null
        assertNull(response.getSentAt(), "sentAt should be null when not set on entity");
        assertNull(response.getResolvedAt(), "resolvedAt should be null when not set on entity");
        assertNull(response.getProblemId(), "problemId should be null when not set on entity");
    }

    @Test
    void testFromEntity_PreservesTimestamps() {
        // Given - specific timestamps to verify exact mapping
        LocalDateTime createdAt = LocalDateTime.of(2025, 1, 15, 10, 30, 0);
        LocalDateTime sentAt = LocalDateTime.of(2025, 1, 15, 10, 31, 0);
        LocalDateTime resolvedAt = LocalDateTime.of(2025, 1, 15, 11, 0, 0);

        Notification notification = new Notification(3L, "Temperature alert", NotificationType.WARNING);
        notification.setId(20L);
        notification.setStatus(NotificationStatus.RESOLVED);
        notification.setCreatedAt(createdAt);
        notification.setSentAt(sentAt);
        notification.setResolvedAt(resolvedAt);

        // When
        NotificationResponse response = NotificationResponse.fromEntity(notification);

        // Then - timestamps must be exactly preserved (same object reference or equal value)
        assertEquals(createdAt, response.getCreatedAt(),
                "createdAt should be exactly preserved from entity");
        assertEquals(sentAt, response.getSentAt(),
                "sentAt should be exactly preserved from entity");
        assertEquals(resolvedAt, response.getResolvedAt(),
                "resolvedAt should be exactly preserved from entity");
    }
}
