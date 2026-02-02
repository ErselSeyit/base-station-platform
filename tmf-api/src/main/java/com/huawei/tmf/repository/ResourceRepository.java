package com.huawei.tmf.repository;

import com.huawei.tmf.model.Resource;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * MongoDB repository for TMF639 Resource entities.
 */
@Repository
public interface ResourceRepository extends MongoRepository<Resource, String> {

    /**
     * Find resource by external ID (e.g., station_id).
     */
    Optional<Resource> findByExternalId(String externalId);

    /**
     * Find resources by category.
     */
    List<Resource> findByCategory(String category);

    /**
     * Find resources by resource type.
     */
    @Query("{ 'resourceSpecification.@type': ?0 }")
    List<Resource> findByResourceType(String resourceType);

    /**
     * Find resources by operational state.
     */
    List<Resource> findByOperationalState(String operationalState);

    /**
     * Find resources by administrative state.
     */
    List<Resource> findByAdministrativeState(String administrativeState);

    /**
     * Find resources by name (partial match, case-insensitive).
     */
    List<Resource> findByNameContainingIgnoreCase(String name);

    /**
     * Find child resources (resources with given parentId in relationships).
     */
    @Query("{ 'resourceRelationship.resource.id': ?0, 'resourceRelationship.relationshipType': 'isPartOf' }")
    List<Resource> findChildrenByParentId(String parentId);

    /**
     * Complex query with multiple optional filters.
     */
    @Query("{ " +
            "$and: [ " +
            "  { $or: [ { 'category': { $exists: false } }, { 'category': ?0 } ] }, " +
            "  { $or: [ { 'resourceSpecification.@type': { $exists: false } }, { 'resourceSpecification.@type': ?1 } ] }, " +
            "  { $or: [ { 'operationalState': { $exists: false } }, { 'operationalState': ?2 } ] }, " +
            "  { $or: [ { 'administrativeState': { $exists: false } }, { 'administrativeState': ?3 } ] } " +
            "] }")
    Page<Resource> findByFilters(String category, String resourceType,
                                  String operationalState, String administrativeState,
                                  Pageable pageable);

    /**
     * Count resources by category.
     */
    long countByCategory(String category);

    /**
     * Count resources by operational state.
     */
    long countByOperationalState(String operationalState);

    /**
     * Check if external ID already exists.
     */
    boolean existsByExternalId(String externalId);
}
