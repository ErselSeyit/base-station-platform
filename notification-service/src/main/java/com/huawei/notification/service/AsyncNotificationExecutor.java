package com.huawei.notification.service;

import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import com.huawei.notification.exception.NotificationException;

/**
 * Handles asynchronous execution of notification operations.
 * Separated from NotificationService to avoid self-injection anti-pattern.
 */
@Component
@SuppressWarnings("null") // CompletableFuture.runAsync guarantees non-null return
public class AsyncNotificationExecutor {

    private static final Logger log = LoggerFactory.getLogger(AsyncNotificationExecutor.class);

    /**
     * Executes a notification send operation asynchronously.
     * Uses @Async for thread management - no need for additional CompletableFuture.runAsync().
     *
     * @param notificationId the notification ID to send
     * @param sendOperation  the operation to execute (typically NotificationService::sendNotification)
     * @return CompletableFuture that completes when the notification is sent
     */
    @Async("notificationExecutor")
    public CompletableFuture<Void> sendAsync(Long notificationId, NotificationSendOperation sendOperation) {
        try {
            log.debug("Async sending notification {}", notificationId);
            sendOperation.send(notificationId);
            return CompletableFuture.completedFuture(null);
        } catch (NotificationException e) {
            log.error("Failed to send notification {}", notificationId, e);
            return CompletableFuture.failedFuture(e);
        } catch (Exception e) {
            log.error("Unexpected error sending notification {}", notificationId, e);
            return CompletableFuture.failedFuture(
                    new NotificationException("Failed to send notification asynchronously", notificationId, e));
        }
    }

    /**
     * Functional interface for notification send operations.
     */
    @FunctionalInterface
    public interface NotificationSendOperation {
        void send(Long notificationId);
    }
}
