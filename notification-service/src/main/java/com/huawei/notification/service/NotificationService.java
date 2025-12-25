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

@Service
@Transactional
@SuppressWarnings("null")
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    
    private final NotificationRepository repository;

    public NotificationService(NotificationRepository repository) {
        this.repository = repository;
    }

    public Notification createNotification(Long stationId, String message, NotificationType type) {
        return repository.save(new Notification(stationId, message, type));
    }

    @Async("notificationExecutor")
    public CompletableFuture<Void> sendNotificationAsync(Long notificationId) {
        return CompletableFuture.runAsync(() -> {
            try {
                sendNotification(notificationId);
            } catch (Exception e) {
                log.error("Error sending notification {}", notificationId, e);
            }
        });
    }

    public void sendNotification(Long notificationId) {
        Notification notification = repository.findById(notificationId)
                .orElseThrow(() -> new IllegalArgumentException("Notification not found: " + notificationId));

        try {
            log.info("Sending notification {} to station {}", notificationId, notification.getStationId());
            Thread.sleep(100); // Simulate network delay

            notification.setStatus(NotificationStatus.SENT);
            notification.setSentAt(LocalDateTime.now());
            repository.save(notification);

            log.info("Notification {} sent successfully", notificationId);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            markAsFailed(notification, e);
        } catch (Exception e) {
            markAsFailed(notification, e);
        }
    }

    private void markAsFailed(Notification notification, Exception e) {
        log.error("Failed to send notification {}", notification.getId(), e);
        notification.setStatus(NotificationStatus.FAILED);
        repository.save(notification);
        throw new RuntimeException("Failed to send notification", e);
    }

    public void processPendingNotifications() {
        List<Notification> pending = repository.findByStatus(NotificationStatus.PENDING);
        log.info("Processing {} pending notifications", pending.size());

        List<CompletableFuture<Void>> futures = pending.stream()
                .filter(n -> n.getId() != null)
                .map(n -> sendNotificationAsync(n.getId()))
                .toList();

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
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
