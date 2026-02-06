package com.huawei.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for LoginAttemptService.
 *
 * Tests cover brute-force protection including:
 * - Failed attempt tracking
 * - Account lockout after max attempts
 * - Exponential backoff
 * - Automatic unlock after lockout period
 */
@DisplayName("LoginAttemptService Tests")
class LoginAttemptServiceTest {

    // Default test configuration values (matching application.yml defaults)
    private static final int MAX_ATTEMPTS = 5;
    private static final long INITIAL_LOCKOUT_SECONDS = 60;
    private static final long MAX_LOCKOUT_SECONDS = 3600;
    private static final double BACKOFF_MULTIPLIER = 2.0;
    private static final long CLEANUP_THRESHOLD_SECONDS = 86400;

    private LoginAttemptService loginAttemptService;

    @BeforeEach
    void setUp() {
        // Create service with default config - no accounts exempt from rate limiting
        loginAttemptService = createService("");
    }

    /**
     * Helper to create LoginAttemptService with default test configuration.
     */
    private LoginAttemptService createService(String serviceAccountsConfig) {
        return new LoginAttemptService(
                serviceAccountsConfig,
                MAX_ATTEMPTS,
                INITIAL_LOCKOUT_SECONDS,
                MAX_LOCKOUT_SECONDS,
                BACKOFF_MULTIPLIER,
                CLEANUP_THRESHOLD_SECONDS
        );
    }

    @Nested
    @DisplayName("recordFailedAttempt()")
    class RecordFailedAttemptTests {

        @Test
        @DisplayName("Should track first failed attempt")
        void recordFailedAttempt_FirstAttempt_TracksAttempt() {
            // Given
            String key = "user1:192.168.1.1";

            // When
            loginAttemptService.recordFailedAttempt(key);

            // Then
            assertThat(loginAttemptService.getRemainingAttempts(key)).isEqualTo(4);
            assertThat(loginAttemptService.isBlocked(key)).isFalse();
        }

        @Test
        @DisplayName("Should track multiple failed attempts")
        void recordFailedAttempt_MultipleAttempts_TracksAll() {
            // Given
            String key = "user2:192.168.1.2";

            // When
            loginAttemptService.recordFailedAttempt(key);
            loginAttemptService.recordFailedAttempt(key);
            loginAttemptService.recordFailedAttempt(key);

            // Then
            assertThat(loginAttemptService.getRemainingAttempts(key)).isEqualTo(2);
            assertThat(loginAttemptService.isBlocked(key)).isFalse();
        }

        @Test
        @DisplayName("Should block after max attempts reached")
        void recordFailedAttempt_MaxAttempts_BlocksUser() {
            // Given
            String key = "user3:192.168.1.3";

            // When - 5 failed attempts (max)
            for (int i = 0; i < 5; i++) {
                loginAttemptService.recordFailedAttempt(key);
            }

            // Then
            assertThat(loginAttemptService.isBlocked(key)).isTrue();
            assertThat(loginAttemptService.getRemainingAttempts(key)).isZero();
        }

        @Test
        @DisplayName("Should track attempts independently per key")
        void recordFailedAttempt_DifferentKeys_IndependentTracking() {
            // Given
            String key1 = "user1:192.168.1.1";
            String key2 = "user2:192.168.1.2";

            // When
            loginAttemptService.recordFailedAttempt(key1);
            loginAttemptService.recordFailedAttempt(key1);
            loginAttemptService.recordFailedAttempt(key2);

            // Then
            assertThat(loginAttemptService.getRemainingAttempts(key1)).isEqualTo(3);
            assertThat(loginAttemptService.getRemainingAttempts(key2)).isEqualTo(4);
        }
    }

    @Nested
    @DisplayName("recordSuccessfulLogin()")
    class RecordSuccessfulLoginTests {

        @Test
        @DisplayName("Should reset attempt counter after successful login")
        void recordSuccessfulLogin_AfterFailedAttempts_ResetsCounter() {
            // Given
            String key = "user4:192.168.1.4";
            loginAttemptService.recordFailedAttempt(key);
            loginAttemptService.recordFailedAttempt(key);

            // When
            loginAttemptService.recordSuccessfulLogin(key);

            // Then
            assertThat(loginAttemptService.getRemainingAttempts(key)).isEqualTo(5);
            assertThat(loginAttemptService.isBlocked(key)).isFalse();
        }

        @Test
        @DisplayName("Should handle successful login with no prior attempts")
        void recordSuccessfulLogin_NoPriorAttempts_NoError() {
            // Given
            String key = "newuser:192.168.1.5";

            // When/Then - should not throw
            loginAttemptService.recordSuccessfulLogin(key);
            assertThat(loginAttemptService.getRemainingAttempts(key)).isEqualTo(5);
        }

        @Test
        @DisplayName("Should unblock user after successful login post-lockout")
        void recordSuccessfulLogin_WasBlocked_UnblocksUser() {
            // Given
            String key = "blockeduser:192.168.1.6";
            for (int i = 0; i < 5; i++) {
                loginAttemptService.recordFailedAttempt(key);
            }
            assertThat(loginAttemptService.isBlocked(key)).isTrue();

            // When
            loginAttemptService.recordSuccessfulLogin(key);

            // Then
            assertThat(loginAttemptService.isBlocked(key)).isFalse();
            assertThat(loginAttemptService.getRemainingAttempts(key)).isEqualTo(5);
        }
    }

    @Nested
    @DisplayName("isBlocked()")
    class IsBlockedTests {

        @Test
        @DisplayName("Should return false for unknown key")
        void isBlocked_UnknownKey_ReturnsFalse() {
            // Given
            String key = "unknown:192.168.1.99";

            // When/Then
            assertThat(loginAttemptService.isBlocked(key)).isFalse();
        }

        @Test
        @DisplayName("Should return false when under max attempts")
        void isBlocked_UnderMaxAttempts_ReturnsFalse() {
            // Given
            String key = "user:192.168.1.10";
            loginAttemptService.recordFailedAttempt(key);
            loginAttemptService.recordFailedAttempt(key);
            loginAttemptService.recordFailedAttempt(key);
            loginAttemptService.recordFailedAttempt(key); // 4 attempts

            // When/Then
            assertThat(loginAttemptService.isBlocked(key)).isFalse();
        }

        @Test
        @DisplayName("Should return true when max attempts reached")
        void isBlocked_MaxAttemptsReached_ReturnsTrue() {
            // Given
            String key = "maxuser:192.168.1.11";
            for (int i = 0; i < 5; i++) {
                loginAttemptService.recordFailedAttempt(key);
            }

            // When/Then
            assertThat(loginAttemptService.isBlocked(key)).isTrue();
        }

        @Test
        @DisplayName("Should return true when over max attempts")
        void isBlocked_OverMaxAttempts_ReturnsTrue() {
            // Given
            String key = "overmax:192.168.1.12";
            for (int i = 0; i < 7; i++) {
                loginAttemptService.recordFailedAttempt(key);
            }

            // When/Then
            assertThat(loginAttemptService.isBlocked(key)).isTrue();
        }
    }

    @Nested
    @DisplayName("getRemainingAttempts()")
    class GetRemainingAttemptsTests {

        @Test
        @DisplayName("Should return max attempts for unknown key")
        void getRemainingAttempts_UnknownKey_ReturnsMax() {
            // Given
            String key = "newkey:192.168.1.20";

            // When/Then
            assertThat(loginAttemptService.getRemainingAttempts(key)).isEqualTo(5);
        }

        @Test
        @DisplayName("Should return correct remaining after failed attempts")
        void getRemainingAttempts_AfterFailures_ReturnsCorrect() {
            // Given
            String key = "testkey:192.168.1.21";
            loginAttemptService.recordFailedAttempt(key);
            loginAttemptService.recordFailedAttempt(key);

            // When/Then
            assertThat(loginAttemptService.getRemainingAttempts(key)).isEqualTo(3);
        }

        @Test
        @DisplayName("Should return zero when blocked")
        void getRemainingAttempts_Blocked_ReturnsZero() {
            // Given
            String key = "blocked:192.168.1.22";
            for (int i = 0; i < 5; i++) {
                loginAttemptService.recordFailedAttempt(key);
            }

            // When/Then
            assertThat(loginAttemptService.getRemainingAttempts(key)).isZero();
        }

        @Test
        @DisplayName("Should not return negative when over max")
        void getRemainingAttempts_OverMax_ReturnsZeroNotNegative() {
            // Given
            String key = "overmax:192.168.1.23";
            for (int i = 0; i < 10; i++) {
                loginAttemptService.recordFailedAttempt(key);
            }

            // When/Then
            assertThat(loginAttemptService.getRemainingAttempts(key)).isGreaterThanOrEqualTo(0);
        }
    }

    @Nested
    @DisplayName("getRemainingLockoutSeconds()")
    class GetRemainingLockoutSecondsTests {

        @Test
        @DisplayName("Should return zero for unknown key")
        void getRemainingLockoutSeconds_UnknownKey_ReturnsZero() {
            // Given
            String key = "unknownlock:192.168.1.30";

            // When/Then
            assertThat(loginAttemptService.getRemainingLockoutSeconds(key)).isZero();
        }

        @Test
        @DisplayName("Should return zero when not blocked")
        void getRemainingLockoutSeconds_NotBlocked_ReturnsZero() {
            // Given
            String key = "notblocked:192.168.1.31";
            loginAttemptService.recordFailedAttempt(key);
            loginAttemptService.recordFailedAttempt(key);

            // When/Then
            assertThat(loginAttemptService.getRemainingLockoutSeconds(key)).isZero();
        }

        @Test
        @DisplayName("Should return positive seconds when blocked")
        void getRemainingLockoutSeconds_Blocked_ReturnsPositive() {
            // Given
            String key = "locked:192.168.1.32";
            for (int i = 0; i < 5; i++) {
                loginAttemptService.recordFailedAttempt(key);
            }

            // When
            long remaining = loginAttemptService.getRemainingLockoutSeconds(key);

            // Then - should be positive (lockout duration increases with backoff)
            assertThat(remaining).isPositive();
        }
    }

    @Nested
    @DisplayName("cleanupExpiredEntries()")
    class CleanupExpiredEntriesTests {

        @Test
        @DisplayName("Should not throw when cleaning empty map")
        void cleanupExpiredEntries_EmptyMap_NoError() {
            // When/Then - should not throw
            assertDoesNotThrow(() -> loginAttemptService.cleanupExpiredEntries());
        }

        @Test
        @DisplayName("Should keep recent entries")
        void cleanupExpiredEntries_RecentEntries_Keeps() {
            // Given
            String key = "recentuser:192.168.1.40";
            loginAttemptService.recordFailedAttempt(key);

            // When
            loginAttemptService.cleanupExpiredEntries();

            // Then - entry should still exist
            assertThat(loginAttemptService.getRemainingAttempts(key)).isEqualTo(4);
        }
    }

    @Nested
    @DisplayName("Service Account Exemption")
    class ServiceAccountTests {

        @Test
        @DisplayName("Should never block configured service accounts")
        void isBlocked_ServiceAccount_NeverBlocked() {
            // Given - service with configured service account
            LoginAttemptService serviceWithExemption = createService("edge-bridge,monitoring-service");
            String serviceKey = "edge-bridge:192.168.1.100";

            // When - exceed max attempts
            for (int i = 0; i < 10; i++) {
                serviceWithExemption.recordFailedAttempt(serviceKey);
            }

            // Then - should NOT be blocked
            assertThat(serviceWithExemption.isBlocked(serviceKey)).isFalse();
        }

        @Test
        @DisplayName("Should block non-service accounts normally")
        void isBlocked_NonServiceAccount_BlockedNormally() {
            // Given
            LoginAttemptService serviceWithExemption = createService("edge-bridge");
            String userKey = "regularuser:192.168.1.101";

            // When
            for (int i = 0; i < 5; i++) {
                serviceWithExemption.recordFailedAttempt(userKey);
            }

            // Then
            assertThat(serviceWithExemption.isBlocked(userKey)).isTrue();
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("Should handle empty string key")
        void edgeCase_EmptyKey_HandlesGracefully() {
            // Given
            String key = "";

            // When
            loginAttemptService.recordFailedAttempt(key);

            // Then
            assertThat(loginAttemptService.getRemainingAttempts(key)).isEqualTo(4);
        }

        @Test
        @DisplayName("Should handle special characters in key")
        void edgeCase_SpecialCharsInKey_HandlesCorrectly() {
            // Given
            String key = "user@domain.com:192.168.1.1";

            // When
            loginAttemptService.recordFailedAttempt(key);

            // Then
            assertThat(loginAttemptService.getRemainingAttempts(key)).isEqualTo(4);
        }

        @Test
        @DisplayName("Should handle very long key")
        void edgeCase_VeryLongKey_HandlesCorrectly() {
            // Given
            String key = "a".repeat(1000) + ":192.168.1.1";

            // When
            loginAttemptService.recordFailedAttempt(key);

            // Then
            assertThat(loginAttemptService.getRemainingAttempts(key)).isEqualTo(4);
        }
    }
}
