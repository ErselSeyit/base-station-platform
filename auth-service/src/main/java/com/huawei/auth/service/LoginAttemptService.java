package com.huawei.auth.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service to track and limit login attempts for brute-force protection.
 *
 * <p>Features:
 * - Tracks failed login attempts per username/IP
 * - Implements exponential backoff
 * - Auto-unlocks after lockout period
 * - Exempts service accounts from rate limiting
 *
 * <p>For production, consider using Redis for distributed caching.
 */
@Service
public class LoginAttemptService {

    private static final Logger log = LoggerFactory.getLogger(LoginAttemptService.class);

    private final int maxAttempts;
    private final long initialLockoutSeconds;
    private final long maxLockoutSeconds;
    private final double backoffMultiplier;
    private final long cleanupThresholdSeconds;

    private final Map<String, AttemptInfo> attempts = new ConcurrentHashMap<>();
    private final Set<String> serviceAccounts;

    /**
     * Constructor with configurable parameters.
     *
     * @param serviceAccountsConfig comma-separated list of service account usernames
     * @param maxAttempts maximum login attempts before lockout
     * @param initialLockoutSeconds initial lockout duration in seconds
     * @param maxLockoutSeconds maximum lockout duration in seconds
     * @param backoffMultiplier multiplier for exponential backoff
     * @param cleanupThresholdSeconds threshold for cleaning up old entries
     */
    public LoginAttemptService(
            @org.springframework.beans.factory.annotation.Value("${auth.service-accounts:}") String serviceAccountsConfig,
            @org.springframework.beans.factory.annotation.Value("${auth.login-attempts.max-attempts:5}") int maxAttempts,
            @org.springframework.beans.factory.annotation.Value("${auth.login-attempts.initial-lockout-seconds:60}") long initialLockoutSeconds,
            @org.springframework.beans.factory.annotation.Value("${auth.login-attempts.max-lockout-seconds:3600}") long maxLockoutSeconds,
            @org.springframework.beans.factory.annotation.Value("${auth.login-attempts.backoff-multiplier:2.0}") double backoffMultiplier,
            @org.springframework.beans.factory.annotation.Value("${auth.login-attempts.cleanup-threshold-seconds:86400}") long cleanupThresholdSeconds) {

        this.maxAttempts = maxAttempts;
        this.initialLockoutSeconds = initialLockoutSeconds;
        this.maxLockoutSeconds = maxLockoutSeconds;
        this.backoffMultiplier = backoffMultiplier;
        this.cleanupThresholdSeconds = cleanupThresholdSeconds;
        // Parse service accounts from config, default to empty if not set
        if (serviceAccountsConfig != null && !serviceAccountsConfig.isBlank()) {
            this.serviceAccounts = java.util.Arrays.stream(serviceAccountsConfig.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(java.util.stream.Collectors.toSet());
            log.info("Configured service accounts exempt from rate limiting: {}", this.serviceAccounts);
        } else {
            this.serviceAccounts = Set.of();
            log.info("No service accounts configured for rate limit exemption");
        }
    }

    /**
     * Checks if the given username is a service account.
     *
     * @param username the username to check
     * @return true if it's a service account
     */
    private boolean isServiceAccount(String username) {
        return username != null && serviceAccounts.stream()
            .anyMatch(sa -> username.trim().equalsIgnoreCase(sa));
    }

    /**
     * Records a failed login attempt.
     *
     * @param key unique identifier (username or IP)
     */
    public void recordFailedAttempt(String key) {
        attempts.compute(key, (k, info) -> {
            if (info == null) {
                return new AttemptInfo(1, Instant.now(), initialLockoutSeconds);
            }

            // Reset if lockout has expired
            if (isLockoutExpired(info)) {
                return new AttemptInfo(1, Instant.now(), initialLockoutSeconds);
            }

            int newCount = info.failedAttempts + 1;
            long newLockoutDuration = Math.min(
                (long) (info.lockoutDurationSeconds * backoffMultiplier),
                maxLockoutSeconds
            );

            if (newCount >= maxAttempts) {
                log.warn("Account locked due to {} failed attempts: {}", newCount, key);
            }

            return new AttemptInfo(newCount, Instant.now(), newLockoutDuration);
        });
    }

    /**
     * Records a successful login (resets attempt counter).
     *
     * @param key unique identifier (username or IP)
     */
    public void recordSuccessfulLogin(String key) {
        attempts.remove(key);
        log.debug("Login successful, attempt counter reset for: {}", key);
    }

    /**
     * Checks if the user/IP is currently blocked.
     *
     * @param key unique identifier (username or IP)
     * @return true if blocked, false otherwise
     */
    public boolean isBlocked(String key) {
        // Extract username from key (format: "username:ip")
        String username = key.contains(":") ? key.split(":")[0] : key;

        // Service accounts are exempt from rate limiting
        if (isServiceAccount(username)) {
            return false;
        }

        AttemptInfo info = attempts.get(key);
        if (info == null || info.failedAttempts < maxAttempts) {
            return false;
        }
        // Blocked if max attempts reached and lockout not yet expired
        return !isLockoutExpired(info);
    }

    /**
     * Gets remaining lockout time in seconds.
     *
     * @param key unique identifier
     * @return seconds until unlock, or 0 if not locked
     */
    public long getRemainingLockoutSeconds(String key) {
        AttemptInfo info = attempts.get(key);
        if (info == null || info.failedAttempts < maxAttempts) {
            return 0;
        }

        long elapsedSeconds = Instant.now().getEpochSecond() - info.lastAttempt.getEpochSecond();
        long remaining = info.lockoutDurationSeconds - elapsedSeconds;
        return Math.max(0, remaining);
    }

    /**
     * Gets the number of remaining attempts before lockout.
     *
     * @param key unique identifier
     * @return remaining attempts
     */
    public int getRemainingAttempts(String key) {
        AttemptInfo info = attempts.get(key);
        if (info == null) {
            return maxAttempts;
        }

        if (isLockoutExpired(info)) {
            return maxAttempts;
        }

        return Math.max(0, maxAttempts - info.failedAttempts);
    }

    private boolean isLockoutExpired(AttemptInfo info) {
        if (info.failedAttempts < maxAttempts) {
            return false;
        }
        long elapsedSeconds = Instant.now().getEpochSecond() - info.lastAttempt.getEpochSecond();
        return elapsedSeconds >= info.lockoutDurationSeconds;
    }

    /**
     * Cleans up expired entries (call periodically in production).
     */
    public void cleanupExpiredEntries() {
        attempts.entrySet().removeIf(entry -> {
            AttemptInfo info = entry.getValue();
            long elapsedSeconds = Instant.now().getEpochSecond() - info.lastAttempt.getEpochSecond();
            // Remove entries older than cleanup threshold
            return elapsedSeconds > cleanupThresholdSeconds;
        });
    }

    private static class AttemptInfo {
        final int failedAttempts;
        final Instant lastAttempt;
        final long lockoutDurationSeconds;

        AttemptInfo(int failedAttempts, Instant lastAttempt, long lockoutDurationSeconds) {
            this.failedAttempts = failedAttempts;
            this.lastAttempt = lastAttempt;
            this.lockoutDurationSeconds = lockoutDurationSeconds;
        }
    }
}
