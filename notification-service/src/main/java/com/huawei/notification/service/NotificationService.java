package com.huawei.notification.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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
     * Creates a notification linked to a diagnostic problem ID.
     * This allows the notification to be resolved when the AI diagnostics resolves the problem.
     *
     * @param stationId the station ID
     * @param message the notification message
     * @param type the notification type
     * @param problemId the problem ID for linking to diagnostic sessions
     * @return the created notification
     */
    public Notification createNotificationWithProblemId(Long stationId, String message, NotificationType type,
                                                         String problemId) {
        Notification notification = new Notification(stationId, message, type);
        notification.setProblemId(problemId);
        return repository.save(notification);
    }

    /**
     * Resolves all notifications linked to a problem ID.
     * Called when AI diagnostics successfully resolves the underlying problem.
     *
     * @param problemId the problem ID to resolve
     * @return the number of notifications resolved
     */
    public int resolveByProblemId(String problemId) {
        if (problemId == null || problemId.isBlank()) {
            log.warn("Cannot resolve notifications - problemId is null or blank");
            return 0;
        }

        List<Notification> notifications = repository.findByProblemId(problemId);
        if (notifications.isEmpty()) {
            log.debug("No notifications found for problemId: {}", problemId);
            return 0;
        }

        int resolved = 0;
        for (Notification notification : notifications) {
            // Only resolve unread notifications
            if (notification.getStatus() == NotificationStatus.UNREAD) {
                notification.setStatus(NotificationStatus.RESOLVED);
                notification.setResolvedAt(LocalDateTime.now());
                repository.save(notification);
                resolved++;
            }
        }

        if (resolved > 0) {
            cachedCounts = null; // Invalidate cache
            log.info("Resolved {} notifications for problemId: {}", resolved, problemId);
        }

        return resolved;
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
        return repository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    @Transactional(readOnly = true)
    public Page<Notification> getAllNotificationsPaged(Pageable pageable) {
        return repository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public Page<Notification> getNotificationsByStatusPaged(NotificationStatus status, Pageable pageable) {
        return repository.findByStatus(status, pageable);
    }

    // Simple in-memory cache for counts (10 second TTL)
    // Thread-safety: volatile + immutable record is sufficient for read-heavy cache
    @SuppressWarnings("java:S3077") // Immutable record is thread-safe with volatile
    private static final long COUNTS_CACHE_TTL_MS = 10_000;
    @org.springframework.lang.Nullable
    private volatile NotificationCounts cachedCounts;
    private volatile long cachedCountsTimestamp;

    /**
     * Gets notification counts by status and type for lightweight dashboard/badge queries.
     * Results are cached for 10 seconds to reduce database load.
     *
     * @return map containing total, unread, alerts, and warnings counts
     */
    @Transactional(readOnly = true)
    public NotificationCounts getCounts() {
        long now = System.currentTimeMillis();
        NotificationCounts cached = cachedCounts;
        if (cached != null && (now - cachedCountsTimestamp) < COUNTS_CACHE_TTL_MS) {
            return cached;
        }

        NotificationCounts counts = new NotificationCounts(
                repository.countByStatus(NotificationStatus.UNREAD),
                repository.countByStatus(NotificationStatus.UNREAD),
                repository.countByTypeAndStatus(NotificationType.ALERT, NotificationStatus.UNREAD),
                repository.countByTypeAndStatus(NotificationType.WARNING, NotificationStatus.UNREAD)
        );
        cachedCounts = counts;
        cachedCountsTimestamp = now;
        return counts;
    }

    /**
     * Record holding notification counts for lightweight queries.
     */
    public record NotificationCounts(long total, long unread, long alerts, long warnings) {}

    /**
     * Gets recent notifications (last 10) for activity feeds.
     * Returns both UNREAD and RESOLVED notifications to show current state.
     * Excludes READ/cleared notifications.
     *
     * @return list of the 10 most recent active notifications
     */
    @Transactional(readOnly = true)
    public List<Notification> getRecentNotifications() {
        return repository.findByStatusIn(
                List.of(NotificationStatus.UNREAD, NotificationStatus.RESOLVED),
                org.springframework.data.domain.PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt"))
        ).getContent();
    }

    /**
     * Deletes a notification (marks as read by removing it).
     *
     * @param notificationId the notification ID to delete
     * @throws NotificationNotFoundException if notification not found
     */
    public void deleteNotification(Long notificationId) {
        if (!repository.existsById(notificationId)) {
            throw new NotificationNotFoundException(notificationId);
        }
        repository.deleteById(notificationId);
        cachedCounts = null;
        log.debug("Notification {} deleted", notificationId);
    }

    /**
     * Deletes all unread notifications (clear all).
     *
     * @return the number of notifications deleted
     */
    public int deleteAllUnread() {
        int deleted = repository.deleteByStatusBulk(NotificationStatus.UNREAD);
        if (deleted > 0) {
            cachedCounts = null;
            log.info("Deleted {} unread notifications", deleted);
        }
        return deleted;
    }
}
