package com.huawei.monitoring.repository;

import com.huawei.monitoring.model.SONRecommendation;
import com.huawei.monitoring.model.SONRecommendation.SONFunction;
import com.huawei.monitoring.model.SONRecommendation.SONStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * MongoDB repository for SON recommendations.
 */
@Repository
public interface SONRecommendationRepository extends MongoRepository<SONRecommendation, String> {

    /**
     * Find all recommendations for a station.
     */
    List<SONRecommendation> findByStationId(Long stationId);

    /**
     * Find all recommendations for a station with pagination.
     */
    Page<SONRecommendation> findByStationId(Long stationId, Pageable pageable);

    /**
     * Find recommendations by status.
     */
    List<SONRecommendation> findByStatus(SONStatus status);

    /**
     * Find recommendations by status with pagination.
     */
    Page<SONRecommendation> findByStatus(SONStatus status, Pageable pageable);

    /**
     * Find pending recommendations for a station.
     */
    List<SONRecommendation> findByStationIdAndStatus(Long stationId, SONStatus status);

    /**
     * Find recommendations by function type.
     */
    List<SONRecommendation> findByFunctionType(SONFunction functionType);

    /**
     * Find recommendations by function type and status.
     */
    List<SONRecommendation> findByFunctionTypeAndStatus(SONFunction functionType, SONStatus status);

    /**
     * Find recommendations created after a certain date.
     */
    List<SONRecommendation> findByCreatedAtAfter(LocalDateTime after);

    /**
     * Find expired recommendations that need cleanup.
     */
    List<SONRecommendation> findByStatusAndExpiresAtBefore(SONStatus status, LocalDateTime before);

    /**
     * Count pending recommendations.
     */
    long countByStatus(SONStatus status);

    /**
     * Count pending recommendations for a station.
     */
    long countByStationIdAndStatus(Long stationId, SONStatus status);

    /**
     * Find recommendations approved by a specific user.
     */
    List<SONRecommendation> findByApprovedBy(String username);

    /**
     * Find recommendations by multiple statuses.
     */
    List<SONRecommendation> findByStatusIn(List<SONStatus> statuses);

    /**
     * Find recommendations requiring approval.
     */
    List<SONRecommendation> findByApprovalRequiredTrueAndStatus(SONStatus status);

    /**
     * Find auto-executable recommendations.
     */
    List<SONRecommendation> findByAutoExecutableTrueAndStatus(SONStatus status);
}
