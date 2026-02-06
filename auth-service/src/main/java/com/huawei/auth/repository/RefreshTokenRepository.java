package com.huawei.auth.repository;

import com.huawei.auth.model.RefreshToken;
import com.huawei.auth.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Repository for refresh token operations.
 */
@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    /**
     * Find a refresh token by its token value.
     */
    Optional<RefreshToken> findByToken(String token);

    /**
     * Find all refresh tokens for a user.
     */
    List<RefreshToken> findByUser(User user);

    /**
     * Find all active (non-revoked, non-expired) tokens for a user.
     */
    @Query("SELECT rt FROM RefreshToken rt WHERE rt.user = :user AND rt.revoked = false AND rt.expiresAt > :now")
    List<RefreshToken> findActiveTokensByUser(@Param("user") User user, @Param("now") Instant now);

    /**
     * Count active tokens for a user.
     */
    @Query("SELECT COUNT(rt) FROM RefreshToken rt WHERE rt.user = :user AND rt.revoked = false AND rt.expiresAt > :now")
    long countActiveTokensByUser(@Param("user") User user, @Param("now") Instant now);

    /**
     * Revoke all tokens for a user.
     */
    @Modifying
    @Query("UPDATE RefreshToken rt SET rt.revoked = true, rt.revokedAt = :now, rt.revokeReason = :reason WHERE rt.user = :user AND rt.revoked = false")
    int revokeAllByUser(@Param("user") User user, @Param("now") Instant now, @Param("reason") String reason);

    /**
     * Revoke a specific token.
     */
    @Modifying
    @Query("UPDATE RefreshToken rt SET rt.revoked = true, rt.revokedAt = :now, rt.revokeReason = :reason WHERE rt.token = :token AND rt.revoked = false")
    int revokeByToken(@Param("token") String token, @Param("now") Instant now, @Param("reason") String reason);

    /**
     * Delete expired tokens (cleanup job).
     */
    @Modifying
    @Query("DELETE FROM RefreshToken rt WHERE rt.expiresAt < :cutoff")
    int deleteExpiredTokens(@Param("cutoff") Instant cutoff);

    /**
     * Delete revoked tokens older than cutoff (cleanup job).
     */
    @Modifying
    @Query("DELETE FROM RefreshToken rt WHERE rt.revoked = true AND rt.revokedAt < :cutoff")
    int deleteOldRevokedTokens(@Param("cutoff") Instant cutoff);

    /**
     * Check if a token exists and is valid.
     */
    @Query("SELECT CASE WHEN COUNT(rt) > 0 THEN true ELSE false END FROM RefreshToken rt WHERE rt.token = :token AND rt.revoked = false AND rt.expiresAt > :now")
    boolean existsValidToken(@Param("token") String token, @Param("now") Instant now);
}
