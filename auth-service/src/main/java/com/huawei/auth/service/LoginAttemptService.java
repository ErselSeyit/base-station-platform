package com.huawei.auth.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service to track and limit login attempts for brute-force protection.
 *
 * <p>Features:
 * - Tracks failed login attempts per username/IP
 * - Implements exponential backoff
 * - Auto-unlocks after lockout period
 *
 * <p>For production, consider using Redis for distributed caching.
 */
@Service
public class LoginAttemptService {

    private static final Logger log = LoggerFactory.getLogger(LoginAttemptService.class);

    private static final int MAX_ATTEMPTS = 5;
    private static final long INITIAL_LOCKOUT_SECONDS = 60; // 1 minute
    private static final long MAX_LOCKOUT_SECONDS = 3600; // 1 hour
    private static final double BACKOFF_MULTIPLIER = 2.0;

    private final Map<String, AttemptInfo> attempts = new ConcurrentHashMap<>();

    /**
     * Records a failed login attempt.
     *
     * @param key unique identifier (username or IP)
     */
    public void recordFailedAttempt(String key) {
        attempts.compute(key, (k, info) -> {
            if (info == null) {
                return new AttemptInfo(1, Instant.now(), INITIAL_LOCKOUT_SECONDS);
            }

            // Reset if lockout has expired
            if (isLockoutExpired(info)) {
                return new AttemptInfo(1, Instant.now(), INITIAL_LOCKOUT_SECONDS);
            }

            int newCount = info.failedAttempts + 1;
            long newLockoutDuration = Math.min(
                (long) (info.lockoutDurationSeconds * BACKOFF_MULTIPLIER),
                MAX_LOCKOUT_SECONDS
            );

            if (newCount >= MAX_ATTEMPTS) {
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
        AttemptInfo info = attempts.get(key);
        if (info == null) {
            return false;
        }

        if (info.failedAttempts < MAX_ATTEMPTS) {
            return false;
        }

        if (isLockoutExpired(info)) {
            // Lockout expired, allow retry but keep some history
            return false;
        }

        return true;
    }

    /**
     * Gets remaining lockout time in seconds.
     *
     * @param key unique identifier
     * @return seconds until unlock, or 0 if not locked
     */
    public long getRemainingLockoutSeconds(String key) {
        AttemptInfo info = attempts.get(key);
        if (info == null || info.failedAttempts < MAX_ATTEMPTS) {
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
            return MAX_ATTEMPTS;
        }

        if (isLockoutExpired(info)) {
            return MAX_ATTEMPTS;
        }

        return Math.max(0, MAX_ATTEMPTS - info.failedAttempts);
    }

    private boolean isLockoutExpired(AttemptInfo info) {
        if (info.failedAttempts < MAX_ATTEMPTS) {
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
            // Remove entries older than 24 hours
            return elapsedSeconds > 86400;
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
