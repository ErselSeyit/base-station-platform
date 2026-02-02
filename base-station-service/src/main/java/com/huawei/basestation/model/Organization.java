package com.huawei.basestation.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * Organization entity for multi-tenancy support.
 *
 * Represents a tenant organization that owns base stations and has users.
 * All data is isolated by organization to support multi-tenant deployments.
 */
@Entity
@Table(name = "organizations", indexes = {
    @Index(name = "idx_org_slug", columnList = "slug", unique = true),
    @Index(name = "idx_org_active", columnList = "active"),
    @Index(name = "idx_org_tier", columnList = "tier")
})
@EntityListeners(AuditingEntityListener.class)
public class Organization {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Organization name is required")
    @Size(min = 2, max = 100)
    @Column(nullable = false)
    private String name;

    @NotBlank(message = "Organization slug is required")
    @Size(min = 2, max = 50)
    @Column(nullable = false, unique = true)
    private String slug;

    @Size(max = 500)
    private String description;

    @Column(nullable = false)
    private boolean active = true;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Tier tier = Tier.STANDARD;

    @Column(name = "max_stations")
    private Integer maxStations = 100;

    @Column(name = "max_users")
    private Integer maxUsers = 50;

    @Size(max = 100)
    @Column(name = "contact_email")
    private String contactEmail;

    @Size(max = 50)
    @Column(name = "contact_phone")
    private String contactPhone;

    @Size(max = 200)
    private String address;

    @Size(max = 100)
    private String country;

    @Size(max = 50)
    private String timezone;

    @Column(name = "logo_url")
    private String logoUrl;

    @ElementCollection
    @CollectionTable(name = "organization_features", joinColumns = @JoinColumn(name = "organization_id"))
    @Column(name = "feature")
    private Set<String> enabledFeatures = new HashSet<>();

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    /**
     * Subscription tiers for organizations.
     */
    public enum Tier {
        FREE,       // Limited features, max 10 stations
        STANDARD,   // Standard features, max 100 stations
        PROFESSIONAL, // Advanced features, max 500 stations
        ENTERPRISE  // All features, unlimited stations
    }

    // Constructors
    public Organization() {
    }

    public Organization(String name, String slug) {
        this.name = name;
        this.slug = slug;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSlug() {
        return slug;
    }

    public void setSlug(String slug) {
        this.slug = slug;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Tier getTier() {
        return tier;
    }

    public void setTier(Tier tier) {
        this.tier = tier;
    }

    public Integer getMaxStations() {
        return maxStations;
    }

    public void setMaxStations(Integer maxStations) {
        this.maxStations = maxStations;
    }

    public Integer getMaxUsers() {
        return maxUsers;
    }

    public void setMaxUsers(Integer maxUsers) {
        this.maxUsers = maxUsers;
    }

    public String getContactEmail() {
        return contactEmail;
    }

    public void setContactEmail(String contactEmail) {
        this.contactEmail = contactEmail;
    }

    public String getContactPhone() {
        return contactPhone;
    }

    public void setContactPhone(String contactPhone) {
        this.contactPhone = contactPhone;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public String getTimezone() {
        return timezone;
    }

    public void setTimezone(String timezone) {
        this.timezone = timezone;
    }

    public String getLogoUrl() {
        return logoUrl;
    }

    public void setLogoUrl(String logoUrl) {
        this.logoUrl = logoUrl;
    }

    public Set<String> getEnabledFeatures() {
        return enabledFeatures;
    }

    public void setEnabledFeatures(Set<String> enabledFeatures) {
        this.enabledFeatures = enabledFeatures;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    // ========================================================================
    // Domain Behavior Methods
    // ========================================================================

    /**
     * Checks if the organization can add more stations.
     *
     * @param currentCount current number of stations
     * @return true if more stations can be added
     */
    public boolean canAddStations(int currentCount) {
        if (tier == Tier.ENTERPRISE) {
            return true; // Unlimited
        }
        return maxStations != null && currentCount < maxStations;
    }

    /**
     * Checks if the organization can add more users.
     *
     * @param currentCount current number of users
     * @return true if more users can be added
     */
    public boolean canAddUsers(int currentCount) {
        if (tier == Tier.ENTERPRISE) {
            return true; // Unlimited
        }
        return maxUsers != null && currentCount < maxUsers;
    }

    /**
     * Checks if a feature is enabled for this organization.
     *
     * @param featureName the feature to check
     * @return true if the feature is enabled
     */
    public boolean hasFeature(String featureName) {
        // Enterprise tier has all features
        if (tier == Tier.ENTERPRISE) {
            return true;
        }
        return enabledFeatures != null && enabledFeatures.contains(featureName);
    }

    /**
     * Enables a feature for this organization.
     *
     * @param featureName the feature to enable
     */
    public void enableFeature(String featureName) {
        if (enabledFeatures == null) {
            enabledFeatures = new HashSet<>();
        }
        enabledFeatures.add(featureName);
    }

    /**
     * Disables a feature for this organization.
     *
     * @param featureName the feature to disable
     */
    public void disableFeature(String featureName) {
        if (enabledFeatures != null) {
            enabledFeatures.remove(featureName);
        }
    }

    /**
     * Deactivates the organization, preventing login and access.
     */
    public void deactivate() {
        this.active = false;
    }

    /**
     * Reactivates the organization.
     */
    public void activate() {
        this.active = true;
    }

    /**
     * Upgrades the organization to a higher tier.
     *
     * @param newTier the new tier
     */
    public void upgradeTier(Tier newTier) {
        if (newTier.ordinal() > this.tier.ordinal()) {
            this.tier = newTier;
            // Update limits based on tier
            switch (newTier) {
                case STANDARD -> {
                    this.maxStations = 100;
                    this.maxUsers = 50;
                }
                case PROFESSIONAL -> {
                    this.maxStations = 500;
                    this.maxUsers = 200;
                }
                case ENTERPRISE -> {
                    this.maxStations = null; // Unlimited
                    this.maxUsers = null;
                }
                default -> { /* No default limits for unknown tiers */ }
            }
        }
    }

    /**
     * Generates a display name for the organization.
     *
     * @return formatted display name
     */
    public String getDisplayName() {
        return name + " (" + slug + ")";
    }
}
