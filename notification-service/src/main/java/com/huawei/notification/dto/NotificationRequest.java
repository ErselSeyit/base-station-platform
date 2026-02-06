package com.huawei.notification.dto;

import com.huawei.notification.model.NotificationType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for creating notifications.
 * Validates input data before entity creation.
 */
public class NotificationRequest {

    @NotNull(message = "Station ID is required")
    @Positive(message = "Station ID must be positive")
    private Long stationId;

    @NotBlank(message = "Message is required")
    @Size(max = 1000, message = "Message cannot exceed 1000 characters")
    private String message;

    @NotNull(message = "Notification type is required")
    private NotificationType type;

    public NotificationRequest() {
    }

    public NotificationRequest(Long stationId, String message, NotificationType type) {
        this.stationId = stationId;
        this.message = message;
        this.type = type;
    }

    public Long getStationId() {
        return stationId;
    }

    public void setStationId(Long stationId) {
        this.stationId = stationId;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public NotificationType getType() {
        return type;
    }

    public void setType(NotificationType type) {
        this.type = type;
    }
}
