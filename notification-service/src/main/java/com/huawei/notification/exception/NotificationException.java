package com.huawei.notification.exception;

/**
 * Exception thrown when a notification operation fails.
 * This includes failures in sending, processing, or delivering notifications.
 */
public class NotificationException extends RuntimeException {

    private final Long notificationId;

    /**
     * Creates a new NotificationException with a message.
     *
     * @param message the error message
     */
    public NotificationException(String message) {
        super(message);
        this.notificationId = null;
    }

    /**
     * Creates a new NotificationException with a message and cause.
     *
     * @param message the error message
     * @param cause   the underlying cause
     */
    public NotificationException(String message, Throwable cause) {
        super(message, cause);
        this.notificationId = null;
    }

    /**
     * Creates a new NotificationException with notification context.
     *
     * @param message        the error message
     * @param notificationId the ID of the notification that failed
     * @param cause          the underlying cause
     */
    public NotificationException(String message, Long notificationId, Throwable cause) {
        super(message, cause);
        this.notificationId = notificationId;
    }

    /**
     * Returns the ID of the notification that failed, if available.
     *
     * @return the notification ID, or null if not available
     */
    public Long getNotificationId() {
        return notificationId;
    }
}
