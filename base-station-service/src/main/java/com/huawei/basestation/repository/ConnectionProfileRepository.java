package com.huawei.basestation.repository;

import com.huawei.basestation.model.ConnectionProfile;
import com.huawei.basestation.model.ConnectionProfile.Protocol;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ConnectionProfileRepository extends JpaRepository<ConnectionProfile, Long> {

    /**
     * Find connection profile by name.
     */
    Optional<ConnectionProfile> findByName(String name);

    /**
     * Find active profiles by protocol.
     */
    List<ConnectionProfile> findByProtocolAndActiveTrue(Protocol protocol);

    /**
     * Find all active profiles.
     */
    List<ConnectionProfile> findByActiveTrue();

    /**
     * Check if name already exists.
     */
    boolean existsByName(String name);
}
