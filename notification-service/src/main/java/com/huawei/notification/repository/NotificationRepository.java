package com.huawei.notification.repository;

import com.huawei.notification.model.Notification;
import com.huawei.notification.model.NotificationStatus;
import com.huawei.notification.model.NotificationType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository interface for Notification entities.
 * 
 * <p>This repository is configured with {@code @NonNullApi} at the package level,
 * which means all method parameters and return values are non-null by default.
 * 
 * <p>Methods returning {@code List} are guaranteed never to return null;
 * they return empty lists when no results are found.
 * 
 * <p>Methods inherited from {@code JpaRepository} like {@code findById()} return
 * {@code Optional<T>}, which uses {@code Optional.empty()} to represent absence
 * rather than null.
 * 
 * @see org.springframework.lang.NonNullApi
 * @see <a href="https://docs.spring.io/spring-data/jpa/reference/repositories/null-handling.html">Spring Data JPA Null Handling</a>
 */
@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {
    
    /**
     * Finds all notifications for a given station ID.
     * 
     * @param stationId the station ID (must not be null)
     * @return a list of notifications (never null, may be empty)
     */
    List<Notification> findByStationId(Long stationId);
    
    /**
     * Finds all notifications with a given status.
     * 
     * @param status the notification status (must not be null)
     * @return a list of notifications (never null, may be empty)
     */
    List<Notification> findByStatus(NotificationStatus status);
    
    /**
     * Finds all notifications of a given type.
     * 
     * @param type the notification type (must not be null)
     * @return a list of notifications (never null, may be empty)
     */
    List<Notification> findByType(NotificationType type);
    
    /**
     * Finds all notifications for a given station ID and status.
     * 
     * @param stationId the station ID (must not be null)
     * @param status the notification status (must not be null)
     * @return a list of notifications (never null, may be empty)
     */
    List<Notification> findByStationIdAndStatus(Long stationId, NotificationStatus status);
}

