package com.huawei.basestation.repository;

import com.huawei.basestation.model.Organization;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for Organization entity.
 */
@Repository
public interface OrganizationRepository extends JpaRepository<Organization, Long> {

    /**
     * Find organization by slug.
     */
    Optional<Organization> findBySlug(String slug);

    /**
     * Find organization by name (case-insensitive).
     */
    Optional<Organization> findByNameIgnoreCase(String name);

    /**
     * Find all active organizations.
     */
    List<Organization> findByActiveTrue();

    /**
     * Find all active organizations with pagination.
     */
    Page<Organization> findByActiveTrue(Pageable pageable);

    /**
     * Find organizations by tier.
     */
    List<Organization> findByTier(Organization.Tier tier);

    /**
     * Check if slug already exists.
     */
    boolean existsBySlug(String slug);

    /**
     * Check if name already exists (case-insensitive).
     */
    boolean existsByNameIgnoreCase(String name);

    /**
     * Search organizations by name or slug containing keyword.
     */
    @Query("SELECT o FROM Organization o WHERE " +
           "LOWER(o.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(o.slug) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    Page<Organization> search(@Param("keyword") String keyword, Pageable pageable);

    /**
     * Count active organizations.
     */
    long countByActiveTrue();

    /**
     * Find organizations by country.
     */
    List<Organization> findByCountry(String country);

    /**
     * Find organizations with a specific feature enabled.
     */
    @Query("SELECT o FROM Organization o JOIN o.enabledFeatures f WHERE f = :feature")
    List<Organization> findByEnabledFeature(@Param("feature") String feature);
}
