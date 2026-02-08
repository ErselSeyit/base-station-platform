package com.huawei.notification.model;

/**
 * Status of a notification.
 * <p>
 * Lifecycle: UNREAD -> READ (user action) or UNREAD -> SENT (if delivery required)
 * </p>
 */
public enum NotificationStatus {
    /** Notification created but not yet viewed by user */
    UNREAD,
    /** User has viewed/acknowledged the notification */
    READ,
    /** Notification pending delivery (email, SMS, etc.) */
    PENDING,
    /** Notification successfully delivered */
    SENT,
    /** Notification delivery failed */
    FAILED
}

