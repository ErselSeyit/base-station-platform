package com.huawei.notification.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.huawei.notification.exception.NotificationException;
import com.huawei.notification.exception.NotificationNotFoundException;
import com.huawei.notification.model.Notification;
import com.huawei.notification.model.NotificationStatus;
import com.huawei.notification.model.NotificationType;
import com.huawei.notification.repository.NotificationRepository;

@Service
@Transactional
@SuppressWarnings("null") // Repository and CompletableFuture operations guarantee non-null values
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository repository;
    private final AsyncNotificationExecutor asyncExecutor;

    public NotificationService(NotificationRepository repository, AsyncNotificationExecutor asyncExecutor) {
        this.repository = repository;
        this.asyncExecutor = asyncExecutor;
    }

    public Notification createNotification(Long stationId, String message, NotificationType type) {
        return repository.save(new Notification(stationId, message, type));
    }

    /**
     * Sends a notification asynchronously.
     *
     * @param notificationId the notification ID to send
     * @return CompletableFuture that completes when the notification is sent
     */
    public CompletableFuture<Void> sendNotificationAsync(Long notificationId) {
        return asyncExecutor.sendAsync(notificationId, this::sendNotification);
    }

    /**
     * Sends a notification synchronously.
     * Uses optimistic locking via @Version to prevent race conditions.
     * Idempotent - will not re-send already sent or failed notifications.
     *
     * @param notificationId the notification ID to send
     * @throws NotificationNotFoundException if notification not found
     * @throws NotificationException if sending fails
     */
    public void sendNotification(Long notificationId) {
        Notification notification = repository.findById(notificationId)
                .orElseThrow(() -> new NotificationNotFoundException(notificationId));

        // Idempotency check - don't re-send if already processed
        if (notification.getStatus() != NotificationStatus.PENDING) {
            log.debug("Notification {} already in {} state, skipping", notificationId, notification.getStatus());
            return;
        }

        try {
            log.info("Sending notification {} to station {}", notificationId, notification.getStationId());

            // In production, this would call actual notification providers (email, SMS, etc.)
            notification.setStatus(NotificationStatus.SENT);
            notification.setSentAt(LocalDateTime.now());
            repository.save(notification);

            log.info("Notification {} sent successfully", notificationId);
        } catch (OptimisticLockingFailureException e) {
            // Another thread already processed this notification - this is expected in concurrent scenarios
            log.warn("Notification {} was modified by another process, skipping", notificationId);
        } catch (Exception e) {
            // Update notification state before rethrowing - caller will log the exception
            notification.incrementRetryCount();
            notification.setLastError(e.getMessage());
            notification.setStatus(NotificationStatus.FAILED);
            repository.save(notification);
            log.warn("Notification {} marked as FAILED after {} attempts", notificationId, notification.getRetryCount());
            throw new NotificationException("Failed to send notification " + notification.getId(), e);
        }
    }

    /**
     * Processes all pending notifications asynchronously.
     * Returns a CompletableFuture that completes when all notifications have been processed.
     *
     * @return CompletableFuture that completes when all notifications are processed
     */
    public CompletableFuture<Void> processPendingNotifications() {
        List<Notification> pending = repository.findByStatus(NotificationStatus.PENDING);
        log.info("Processing {} pending notifications", pending.size());

        if (pending.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }

        List<CompletableFuture<Void>> futures = pending.stream()
                .filter(n -> n.getId() != null)
                .map(n -> sendNotificationAsync(n.getId()))
                .toList();

        // Return the combined future with timeout to prevent indefinite hangs
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .orTimeout(5, TimeUnit.MINUTES)
                .thenRun(() -> log.info("Completed processing {} pending notifications", pending.size()));
    }

    @Transactional(readOnly = true)
    public List<Notification> getNotificationsByStation(Long stationId) {
        return repository.findByStationId(stationId);
    }

    @Transactional(readOnly = true)
    public List<Notification> getNotificationsByStatus(NotificationStatus status) {
        return repository.findByStatus(status);
    }

    @Transactional(readOnly = true)
    public List<Notification> getAllNotifications() {
        return repository.findAll();
    }
}
