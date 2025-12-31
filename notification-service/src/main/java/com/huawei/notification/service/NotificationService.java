package com.huawei.notification.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.huawei.notification.model.Notification;
import com.huawei.notification.model.NotificationStatus;
import com.huawei.notification.model.NotificationType;
import com.huawei.notification.repository.NotificationRepository;
import com.huawei.notification.exception.NotificationException;
import org.springframework.context.annotation.Lazy;

@Service
@Transactional
@SuppressWarnings("null") // Repository and CompletableFuture operations guarantee non-null values

public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository repository;
    private final NotificationService self;

    // @Lazy ekleyerek Spring'e "Bu nesneye hemen ihtiyacım yok, 
    // uygulama ayağa kalkınca bağla" diyoruz.
    public NotificationService(NotificationRepository repository, @Lazy NotificationService self) {
        this.repository = repository;
        this.self = self;
    }

    public Notification createNotification(Long stationId, String message, NotificationType type) {
        return repository.save(new Notification(stationId, message, type));
    }

    @Async("notificationExecutor")
    public CompletableFuture<Void> sendNotificationAsync(Long notificationId) {
        return CompletableFuture.runAsync(() -> {
            try {
                sendNotification(notificationId);
            } catch (NotificationException e) {
                throw e;
            } catch (Exception e) {
                throw new NotificationException("Failed to send notification asynchronously", notificationId, e);
            }
        });
    }

    public void sendNotification(Long notificationId) {
        Notification notification = repository.findById(notificationId)
                .orElseThrow(() -> new IllegalArgumentException("Notification not found: " + notificationId));

        try {
            log.info("Sending notification {} to station {}", notificationId, notification.getStationId());

            // In production, this would call actual notification providers (email, SMS, etc.)
            // For now, we simulate the notification sending without blocking threads
            // The actual implementation would be async and non-blocking

            notification.setStatus(NotificationStatus.SENT);
            notification.setSentAt(LocalDateTime.now());
            repository.save(notification);

            log.info("Notification {} sent successfully", notificationId);
        } catch (Exception e) {
            notification.setStatus(NotificationStatus.FAILED);
            repository.save(notification);
            throw new NotificationException("Failed to send notification " + notification.getId(), e);
        }
    }

    /**
     * Processes all pending notifications asynchronously.
     * Returns a CompletableFuture that completes when all notifications have been processed.
     *
     * Note: @Async annotation removed - redundancy with CompletableFuture return type.
     * Let caller control async execution via the returned CompletableFuture.
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
                .map(n -> self.sendNotificationAsync(n.getId()))
                .toList();

        // Return the combined future instead of blocking with join()
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
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
