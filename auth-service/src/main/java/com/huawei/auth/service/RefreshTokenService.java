package com.huawei.auth.service;

import com.huawei.auth.model.RefreshToken;
import com.huawei.auth.model.User;
import com.huawei.auth.repository.RefreshTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

/**
 * Service for managing refresh tokens.
 *
 * <p>Refresh tokens have a longer lifetime than access tokens and can be used
 * to obtain new access tokens without re-authenticating. They can be revoked
 * individually or in bulk.
 */
@Service
public class RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);

    private final RefreshTokenRepository refreshTokenRepository;
    private final SecurityAuditService auditService;

    @Value("${jwt.refresh.expiration:604800000}") // 7 days default
    private long refreshTokenExpirationMs;

    @Value("${jwt.refresh.max-per-user:5}") // Max concurrent tokens per user
    private int maxTokensPerUser;

    public RefreshTokenService(RefreshTokenRepository refreshTokenRepository,
                                SecurityAuditService auditService) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.auditService = auditService;
    }

    /**
     * Create a new refresh token for a user.
     */
    @Transactional
    public RefreshToken createRefreshToken(User user, String clientIp, String userAgent) {
        // Check if user has too many active tokens
        long activeCount = refreshTokenRepository.countActiveTokensByUser(user, Instant.now());
        if (activeCount >= maxTokensPerUser) {
            // Revoke oldest tokens to make room
            revokeOldestTokens(user, (int)(activeCount - maxTokensPerUser + 1));
        }

        return doCreateRefreshToken(user, clientIp, userAgent);
    }

    /**
     * Internal method to create and save a refresh token.
     * Called within an existing transaction context.
     */
    private RefreshToken doCreateRefreshToken(User user, String clientIp, String userAgent) {
        RefreshToken refreshToken = new RefreshToken(user, refreshTokenExpirationMs);
        refreshToken.setClientIp(clientIp);
        refreshToken.setUserAgent(truncate(userAgent, 255));

        RefreshToken saved = refreshTokenRepository.save(refreshToken);
        log.info("Created refresh token for user '{}' from IP '{}'", user.getUsername(), clientIp);
        auditService.logRefreshTokenCreated(user.getUsername(), clientIp);

        return saved;
    }

    /**
     * Verify and return a refresh token.
     */
    @Transactional(readOnly = true)
    public Optional<RefreshToken> verifyRefreshToken(String token) {
        Optional<RefreshToken> refreshTokenOpt = refreshTokenRepository.findByToken(token);

        if (refreshTokenOpt.isEmpty()) {
            log.warn("Refresh token not found");
            return Optional.empty();
        }

        RefreshToken refreshToken = refreshTokenOpt.get();

        if (refreshToken.isRevoked()) {
            log.warn("Attempt to use revoked refresh token for user '{}'",
                    refreshToken.getUser().getUsername());
            auditService.logRefreshTokenRevoked(
                    refreshToken.getUser().getUsername(),
                    refreshToken.getClientIp(),
                    "Token was already revoked"
            );
            return Optional.empty();
        }

        if (refreshToken.isExpired()) {
            log.warn("Attempt to use expired refresh token for user '{}'",
                    refreshToken.getUser().getUsername());
            return Optional.empty();
        }

        return refreshTokenOpt;
    }

    /**
     * Revoke a specific refresh token.
     */
    @Transactional
    public boolean revokeToken(String token, String reason) {
        int updated = refreshTokenRepository.revokeByToken(token, Instant.now(), reason);
        if (updated > 0) {
            log.info("Revoked refresh token: {}", reason);
            return true;
        }
        return false;
    }

    /**
     * Revoke all refresh tokens for a user (e.g., on password change).
     */
    @Transactional
    public int revokeAllUserTokens(User user, String reason) {
        int count = refreshTokenRepository.revokeAllByUser(user, Instant.now(), reason);
        log.info("Revoked {} refresh tokens for user '{}': {}", count, user.getUsername(), reason);
        auditService.logAllRefreshTokensRevoked(user.getUsername(), reason, count);
        return count;
    }

    /**
     * Get all active tokens for a user.
     */
    @Transactional(readOnly = true)
    public List<RefreshToken> getActiveTokens(User user) {
        return refreshTokenRepository.findActiveTokensByUser(user, Instant.now());
    }

    /**
     * Rotate a refresh token (revoke old, create new).
     * This is a security best practice for refresh token rotation.
     */
    @Transactional
    public RefreshToken rotateRefreshToken(RefreshToken oldToken, String clientIp, String userAgent) {
        User user = oldToken.getUser();

        // Revoke the old token
        oldToken.revoke("Token rotation");
        refreshTokenRepository.save(oldToken);

        // Create a new token (no need to check limits since we just revoked one)
        RefreshToken newToken = doCreateRefreshToken(user, clientIp, userAgent);

        log.info("Rotated refresh token for user '{}'", user.getUsername());
        auditService.logRefreshTokenRotated(user.getUsername(), clientIp);

        return newToken;
    }

    /**
     * Revoke oldest tokens for a user to enforce max limit.
     */
    private void revokeOldestTokens(User user, int count) {
        List<RefreshToken> activeTokens = refreshTokenRepository.findActiveTokensByUser(user, Instant.now());

        // Sort by creation time (oldest first)
        activeTokens.sort((a, b) -> a.getCreatedAt().compareTo(b.getCreatedAt()));

        int revoked = 0;
        for (RefreshToken token : activeTokens) {
            if (revoked >= count) break;
            token.revoke("Max tokens exceeded");
            refreshTokenRepository.save(token);
            revoked++;
        }

        log.info("Revoked {} oldest refresh tokens for user '{}' (max limit enforcement)",
                revoked, user.getUsername());
    }

    /**
     * Cleanup expired and old revoked tokens.
     * Runs daily at 2 AM.
     */
    @Scheduled(cron = "0 0 2 * * *")
    @Transactional
    public void cleanupExpiredTokens() {
        Instant expiredCutoff = Instant.now();
        int deletedExpired = refreshTokenRepository.deleteExpiredTokens(expiredCutoff);

        // Keep revoked tokens for 30 days for audit purposes
        Instant revokedCutoff = Instant.now().minus(30, ChronoUnit.DAYS);
        int deletedRevoked = refreshTokenRepository.deleteOldRevokedTokens(revokedCutoff);

        log.info("Cleaned up {} expired and {} old revoked refresh tokens",
                deletedExpired, deletedRevoked);
    }

    /**
     * Get the refresh token expiration in milliseconds.
     */
    public long getRefreshTokenExpirationMs() {
        return refreshTokenExpirationMs;
    }

    private String truncate(String value, int maxLength) {
        if (value == null) return null;
        return value.length() > maxLength ? value.substring(0, maxLength) : value;
    }
}
