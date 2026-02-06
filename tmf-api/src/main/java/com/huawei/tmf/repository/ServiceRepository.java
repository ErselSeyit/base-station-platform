package com.huawei.tmf.repository;

import com.huawei.tmf.model.Service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * MongoDB repository for TMF638 Service entities.
 */
@Repository
public interface ServiceRepository extends MongoRepository<Service, String> {

    /**
     * Find service by external ID.
     */
    Optional<Service> findByExternalId(String externalId);

    /**
     * Find services by category.
     */
    List<Service> findByCategory(String category);

    /**
     * Find services by state.
     */
    List<Service> findByState(String state);

    /**
     * Find services by state (paginated).
     */
    Page<Service> findByState(String state, Pageable pageable);

    /**
     * Find services by service type.
     */
    List<Service> findByServiceType(String serviceType);

    /**
     * Find services by name (partial match, case-insensitive).
     */
    List<Service> findByNameContainingIgnoreCase(String name);

    /**
     * Find active services.
     */
    List<Service> findByStateAndIsServiceEnabled(String state, boolean isServiceEnabled);

    /**
     * Find services by supporting resource.
     */
    @Query("{ 'supportingResource.id': ?0 }")
    List<Service> findBysSupportingResourceId(String resourceId);

    /**
     * Find services by related party.
     */
    @Query("{ 'relatedParty.id': ?0 }")
    List<Service> findByRelatedPartyId(String partyId);

    /**
     * Find services by related party role.
     */
    @Query("{ 'relatedParty.role': ?0 }")
    List<Service> findByRelatedPartyRole(String role);

    /**
     * Find customer-facing services (CFS).
     */
    @Query("{ 'category': 'CFS' }")
    Page<Service> findCustomerFacingServices(Pageable pageable);

    /**
     * Find resource-facing services (RFS).
     */
    @Query("{ 'category': 'RFS' }")
    Page<Service> findResourceFacingServices(Pageable pageable);

    /**
     * Count services by state.
     */
    long countByState(String state);

    /**
     * Count services by category.
     */
    long countByCategory(String category);

    /**
     * Check if external ID already exists.
     */
    boolean existsByExternalId(String externalId);

    /**
     * Find services by service specification.
     */
    @Query("{ 'serviceSpecification.id': ?0 }")
    List<Service> findByServiceSpecificationId(String specificationId);
}
