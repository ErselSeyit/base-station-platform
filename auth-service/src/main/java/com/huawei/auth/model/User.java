package com.huawei.auth.model;

import com.huawei.common.constants.TimeConstants;
import com.huawei.common.security.Roles;
import jakarta.persistence.*;
import java.time.Instant;

/**
 * User entity for authentication.
 */
@Entity
@Table(name = "users", indexes = {
    @Index(name = "idx_user_username", columnList = "username"),
    @Index(name = "idx_user_enabled", columnList = "enabled"),
    @Index(name = "idx_user_created_at", columnList = "created_at")
})
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 50)
    private String username;

    @Column(nullable = false)
    private String passwordHash;

    @Column(nullable = false, length = 50)
    private String role;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    // ========================================================================
    // Domain Behavior Methods
    // ========================================================================

    /**
     * Checks if the user can log in (account is enabled).
     *
     * @return true if the user account is active and can authenticate
     */
    public boolean canLogin() {
        return this.enabled;
    }

    /**
     * Disables the user account, preventing login.
     */
    public void disable() {
        this.enabled = false;
    }

    /**
     * Enables the user account, allowing login.
     */
    public void enable() {
        this.enabled = true;
    }

    /**
     * Checks if the user has the ADMIN role.
     *
     * @return true if the user is an administrator
     */
    public boolean isAdmin() {
        return Roles.isAdmin(this.role);
    }

    /**
     * Checks if the user has the OPERATOR role.
     *
     * @return true if the user is an operator
     */
    public boolean isOperator() {
        return Roles.OPERATOR.equalsIgnoreCase(this.role);
    }

    /**
     * Checks if the user has a specific role.
     *
     * @param roleName the role to check (case-insensitive)
     * @return true if the user has the specified role
     */
    public boolean hasRole(String roleName) {
        if (roleName == null || this.role == null) {
            return false;
        }
        return this.role.equalsIgnoreCase(roleName);
    }

    /**
     * Checks if this is a service account (used for machine-to-machine auth).
     *
     * @return true if this is a service account
     */
    public boolean isServiceAccount() {
        return "SERVICE".equalsIgnoreCase(this.role)
                || (this.username != null && this.username.startsWith("svc-"));
    }

    /**
     * Checks if the account was created recently (within the last 24 hours).
     *
     * @return true if the account is new
     */
    public boolean isNewAccount() {
        if (this.createdAt == null) {
            return false;
        }
        return this.createdAt.isAfter(Instant.now().minusSeconds(TimeConstants.SECONDS_PER_DAY));
    }
}
