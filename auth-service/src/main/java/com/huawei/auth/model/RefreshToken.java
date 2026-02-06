package com.huawei.auth.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Refresh token entity for long-lived authentication sessions.
 *
 * <p>Refresh tokens allow users to obtain new access tokens without
 * re-authenticating. They have a longer lifetime than access tokens
 * but can be revoked individually or in bulk.
 */
@Entity
@Table(name = "refresh_tokens", indexes = {
    @Index(name = "idx_refresh_token", columnList = "token", unique = true),
    @Index(name = "idx_refresh_user", columnList = "user_id"),
    @Index(name = "idx_refresh_expiry", columnList = "expires_at"),
    @Index(name = "idx_refresh_revoked", columnList = "revoked")
})
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 36)
    private String token;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private boolean revoked = false;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "revoke_reason", length = 255)
    private String revokeReason;

    @Column(name = "client_ip", length = 45)
    private String clientIp;

    @Column(name = "user_agent", length = 255)
    private String userAgent;

    @Column(name = "device_id", length = 64)
    private String deviceId;

    public RefreshToken() {
        this.token = UUID.randomUUID().toString();
        this.createdAt = Instant.now();
    }

    public RefreshToken(User user, long expirationMs) {
        this();
        this.user = user;
        this.expiresAt = Instant.now().plusMillis(expirationMs);
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    // Getters and setters

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public boolean isRevoked() {
        return revoked;
    }

    public void setRevoked(boolean revoked) {
        this.revoked = revoked;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public void setRevokedAt(Instant revokedAt) {
        this.revokedAt = revokedAt;
    }

    public String getRevokeReason() {
        return revokeReason;
    }

    public void setRevokeReason(String revokeReason) {
        this.revokeReason = revokeReason;
    }

    public String getClientIp() {
        return clientIp;
    }

    public void setClientIp(String clientIp) {
        this.clientIp = clientIp;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    // Domain methods

    /**
     * Check if the token is expired.
     */
    public boolean isExpired() {
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }

    /**
     * Check if the token is valid (not expired and not revoked).
     */
    public boolean isValid() {
        return !isExpired() && !revoked;
    }

    /**
     * Revoke the token.
     */
    public void revoke(String reason) {
        this.revoked = true;
        this.revokedAt = Instant.now();
        this.revokeReason = reason;
    }

    /**
     * Get remaining time until expiration in seconds.
     */
    public long getRemainingSeconds() {
        if (expiresAt == null) {
            return 0;
        }
        long remaining = expiresAt.getEpochSecond() - Instant.now().getEpochSecond();
        return Math.max(0, remaining);
    }
}
