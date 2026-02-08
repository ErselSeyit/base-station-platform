package com.huawei.notification.repository;

import com.huawei.notification.model.Notification;
import com.huawei.notification.model.NotificationStatus;
import com.huawei.notification.model.NotificationType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    /**
     * Finds all notifications with a given status (paginated).
     *
     * @param status the notification status (must not be null)
     * @param pageable pagination parameters
     * @return a page of notifications (never null)
     */
    Page<Notification> findByStatus(NotificationStatus status, Pageable pageable);

    /**
     * Counts notifications with a given status.
     *
     * @param status the notification status (must not be null)
     * @return the count of matching notifications
     */
    long countByStatus(NotificationStatus status);

    /**
     * Counts notifications of a given type.
     *
     * @param type the notification type (must not be null)
     * @return the count of matching notifications
     */
    long countByType(NotificationType type);

    /**
     * Counts notifications of a given type and status.
     *
     * @param type the notification type (must not be null)
     * @param status the notification status (must not be null)
     * @return the count of matching notifications
     */
    long countByTypeAndStatus(NotificationType type, NotificationStatus status);

    /**
     * Bulk delete: removes all notifications with given status.
     *
     * @param status the status to match for deletion
     * @return the number of deleted notifications
     */
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("DELETE FROM Notification n WHERE n.status = :status")
    int deleteByStatusBulk(@org.springframework.data.repository.query.Param("status") NotificationStatus status);
}

