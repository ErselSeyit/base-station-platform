package com.huawei.notification.exception;

/**
 * Exception thrown when a notification is not found.
 */
public class NotificationNotFoundException extends RuntimeException {

    public NotificationNotFoundException(Long notificationId) {
        super("Notification not found: " + notificationId);
    }

    public NotificationNotFoundException(String message) {
        super(message);
    }
}
