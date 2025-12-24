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

@Repository
public interface BaseStationRepository extends JpaRepository<BaseStation, Long> {
    
    Optional<BaseStation> findByStationName(String stationName);
    
    List<BaseStation> findByStatus(StationStatus status);
    
    List<BaseStation> findByStationType(StationType stationType);
    
    @Query("SELECT bs FROM BaseStation bs WHERE bs.latitude BETWEEN :minLat AND :maxLat " +
           "AND bs.longitude BETWEEN :minLon AND :maxLon")
    List<BaseStation> findStationsInArea(@Param("minLat") Double minLat, 
                                         @Param("maxLat") Double maxLat,
                                         @Param("minLon") Double minLon, 
                                         @Param("maxLon") Double maxLon);
    
    @Query("SELECT COUNT(bs) FROM BaseStation bs WHERE bs.status = :status")
    Long countByStatus(@Param("status") StationStatus status);
}

