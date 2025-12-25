package com.huawei.notification.service;

import java.util.Objects;

import com.huawei.notification.model.Notification;
import com.huawei.notification.model.NotificationStatus;
import com.huawei.notification.model.NotificationType;
import com.huawei.notification.repository.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
@Transactional
public class NotificationService {

    private static final Logger logger = LoggerFactory.getLogger(NotificationService.class);
    private final NotificationRepository repository;
    private final ExecutorService executorService;

    @Autowired
    public NotificationService(NotificationRepository repository) {
        this.repository = repository;
        // Thread pool for concurrent notification processing
        this.executorService = Executors.newFixedThreadPool(10);
    }

    public Notification createNotification(Long stationId, String message, NotificationType type) {
        Notification notification = new Notification(stationId, message, type);
        return repository.save(notification);
    }

    @Async("notificationExecutor")
    public CompletableFuture<Void> sendNotificationAsync(Long notificationId) {
        return CompletableFuture.runAsync(() -> {
            try {
                sendNotification(notificationId);
            } catch (Exception e) {
                logger.error("Error sending notification {}", notificationId, e);
            }
        }, executorService);
    }

    public void sendNotification(Long notificationId) {
        Notification notification = repository.findById(Objects.requireNonNull(notificationId))
                .orElseThrow(() -> new IllegalArgumentException("Notification not found: " + notificationId));

        try {
            // Simulate notification sending (email, SMS, push notification, etc.)
            logger.info("Sending notification {} to station {}", notificationId, notification.getStationId());
            Thread.sleep(100); // Simulate network delay

            notification.setStatus(NotificationStatus.SENT);
            notification.setSentAt(LocalDateTime.now());
            repository.save(notification);

            logger.info("Notification {} sent successfully", notificationId);
        } catch (Exception e) {
            logger.error("Failed to send notification {}", notificationId, e);
            notification.setStatus(NotificationStatus.FAILED);
            repository.save(notification);
            throw new RuntimeException("Failed to send notification", e);
        }
    }

    public void processPendingNotifications() {
        List<Notification> pendingNotifications = repository.findByStatus(NotificationStatus.PENDING);
        logger.info("Processing {} pending notifications", pendingNotifications.size());

        // Process notifications concurrently using thread pool
        List<CompletableFuture<Void>> futures = pendingNotifications.stream()
                .map(notification -> sendNotificationAsync(notification.getId()))
                .toList();

        // Wait for all notifications to be processed
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

