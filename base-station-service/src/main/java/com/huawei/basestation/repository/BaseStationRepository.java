package com.huawei.basestation.repository;

import com.huawei.basestation.model.BaseStation;
import com.huawei.basestation.model.Organization;
import com.huawei.basestation.model.StationStatus;
import com.huawei.basestation.model.StationType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for BaseStation entities.
 * 
 * <p>This repository is configured with {@code @NonNullApi} at the package level,
 * which means all method parameters and return values are non-null by default.
 * 
 * <p>Methods returning {@code List} are guaranteed never to return null;
 * they return empty lists when no results are found.
 * 
 * <p>Methods returning {@code Optional} use {@code Optional.empty()} to represent absence
 * rather than null.
 * 
 * @see org.springframework.lang.NonNullApi
 * @see <a href="https://docs.spring.io/spring-data/jpa/reference/repositories/null-handling.html">Spring Data JPA Null Handling</a>
 */
@Repository
public interface BaseStationRepository extends JpaRepository<BaseStation, Long> {
    
    /**
     * Finds a base station by its station name.
     * 
     * @param stationName the station name (must not be null)
     * @return an Optional containing the base station if found, empty otherwise (never null)
     */
    Optional<BaseStation> findByStationName(String stationName);
    
    /**
     * Finds all base stations with a given status.
     * 
     * @param status the station status (must not be null)
     * @return a list of base stations (never null, may be empty)
     */
    List<BaseStation> findByStatus(StationStatus status);
    
    /**
     * Finds all base stations of a given type.
     * 
     * @param stationType the station type (must not be null)
     * @return a list of base stations (never null, may be empty)
     */
    List<BaseStation> findByStationType(StationType stationType);
    
    /**
     * Finds all base stations within a geographic area defined by latitude and longitude bounds.
     * 
     * @param minLat minimum latitude (must not be null)
     * @param maxLat maximum latitude (must not be null)
     * @param minLon minimum longitude (must not be null)
     * @param maxLon maximum longitude (must not be null)
     * @return a list of base stations within the area (never null, may be empty)
     */
    @Query("SELECT bs FROM BaseStation bs WHERE bs.latitude BETWEEN :minLat AND :maxLat " +
           "AND bs.longitude BETWEEN :minLon AND :maxLon")
    List<BaseStation> findStationsInArea(@Param("minLat") Double minLat, 
                                         @Param("maxLat") Double maxLat,
                                         @Param("minLon") Double minLon, 
                                         @Param("maxLon") Double maxLon);
    
    /**
     * Counts base stations with a given status.
     * 
     * @param status the station status (must not be null)
     * @return the count of base stations (never null)
     */
    @Query("SELECT COUNT(bs) FROM BaseStation bs WHERE bs.status = :status")
    Long countByStatus(@Param("status") StationStatus status);

    /**
     * Finds all base stations within a given radius of a point using the Haversine formula.
     * 
     * This is a proper geospatial "near" query that calculates actual distance
     * on the Earth's surface, not just bounding box filtering.
     * 
     * @param lat center point latitude
     * @param lon center point longitude  
     * @param radiusKm radius in kilometers
     * @return stations within the radius, ordered by distance (never null)
     */
    @Query(value = """
            SELECT * FROM base_stations bs
            WHERE (
                6371 * acos(
                    cos(radians(:lat)) * cos(radians(bs.latitude)) *
                    cos(radians(bs.longitude) - radians(:lon)) +
                    sin(radians(:lat)) * sin(radians(bs.latitude))
                )
            ) <= :radiusKm
            ORDER BY (
                6371 * acos(
                    cos(radians(:lat)) * cos(radians(bs.latitude)) *
                    cos(radians(bs.longitude) - radians(:lon)) +
                    sin(radians(:lat)) * sin(radians(bs.latitude))
                )
            )
            """, nativeQuery = true)
    List<BaseStation> findStationsNearPoint(
            @Param("lat") Double lat,
            @Param("lon") Double lon,
            @Param("radiusKm") Double radiusKm);

    // ========================================================================
    // Multi-tenancy (Organization-scoped) methods
    // ========================================================================

    /**
     * Finds all base stations belonging to an organization.
     *
     * @param organization the organization (must not be null)
     * @return a list of base stations (never null, may be empty)
     */
    List<BaseStation> findByOrganization(Organization organization);

    /**
     * Finds all base stations belonging to an organization with pagination.
     *
     * @param organization the organization (must not be null)
     * @param pageable pagination parameters
     * @return a page of base stations
     */
    Page<BaseStation> findByOrganization(Organization organization, Pageable pageable);

    /**
     * Finds all base stations by organization ID.
     *
     * @param organizationId the organization ID (must not be null)
     * @return a list of base stations (never null, may be empty)
     */
    List<BaseStation> findByOrganizationId(Long organizationId);

    /**
     * Finds all base stations by organization ID with pagination.
     *
     * @param organizationId the organization ID (must not be null)
     * @param pageable pagination parameters
     * @return a page of base stations
     */
    Page<BaseStation> findByOrganizationId(Long organizationId, Pageable pageable);

    /**
     * Finds base stations by organization and status.
     *
     * @param organizationId the organization ID
     * @param status the station status
     * @return a list of base stations
     */
    List<BaseStation> findByOrganizationIdAndStatus(Long organizationId, StationStatus status);

    /**
     * Finds base stations by organization and type.
     *
     * @param organizationId the organization ID
     * @param stationType the station type
     * @return a list of base stations
     */
    List<BaseStation> findByOrganizationIdAndStationType(Long organizationId, StationType stationType);

    /**
     * Counts base stations by organization.
     *
     * @param organizationId the organization ID
     * @return the count
     */
    long countByOrganizationId(Long organizationId);

    /**
     * Counts base stations by organization and status.
     *
     * @param organizationId the organization ID
     * @param status the station status
     * @return the count
     */
    long countByOrganizationIdAndStatus(Long organizationId, StationStatus status);

    /**
     * Finds a station by name within an organization.
     *
     * @param stationName the station name
     * @param organizationId the organization ID
     * @return the station if found
     */
    Optional<BaseStation> findByStationNameAndOrganizationId(String stationName, Long organizationId);

    /**
     * Finds stations in area scoped by organization.
     *
     * @param organizationId the organization ID
     * @param minLat minimum latitude
     * @param maxLat maximum latitude
     * @param minLon minimum longitude
     * @param maxLon maximum longitude
     * @return stations within the area for the organization
     */
    @Query("SELECT bs FROM BaseStation bs WHERE bs.organization.id = :orgId " +
           "AND bs.latitude BETWEEN :minLat AND :maxLat " +
           "AND bs.longitude BETWEEN :minLon AND :maxLon")
    List<BaseStation> findStationsInAreaByOrganization(
            @Param("orgId") Long organizationId,
            @Param("minLat") Double minLat,
            @Param("maxLat") Double maxLat,
            @Param("minLon") Double minLon,
            @Param("maxLon") Double maxLon);

    /**
     * Search stations by name or location within an organization.
     *
     * @param organizationId the organization ID
     * @param keyword search keyword
     * @param pageable pagination parameters
     * @return matching stations
     */
    @Query("SELECT bs FROM BaseStation bs WHERE bs.organization.id = :orgId " +
           "AND (LOWER(bs.stationName) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "OR LOWER(bs.location) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    Page<BaseStation> searchByOrganization(
            @Param("orgId") Long organizationId,
            @Param("keyword") String keyword,
            Pageable pageable);
}

