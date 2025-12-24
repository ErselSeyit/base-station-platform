package com.huawei.notification.repository;

import com.huawei.notification.model.Notification;
import com.huawei.notification.model.NotificationStatus;
import com.huawei.notification.model.NotificationType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {
    
    List<Notification> findByStationId(Long stationId);
    
    List<Notification> findByStatus(NotificationStatus status);
    
    List<Notification> findByType(NotificationType type);
    
    List<Notification> findByStationIdAndStatus(Long stationId, NotificationStatus status);
}

