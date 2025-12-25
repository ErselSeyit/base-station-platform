package com.huawei.basestation.repository;

import com.huawei.basestation.model.BaseStation;
import com.huawei.basestation.model.StationStatus;
import com.huawei.basestation.model.StationType;
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
}

