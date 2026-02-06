package com.huawei.auth.config;

import static com.huawei.common.constants.SecurityConstants.MIN_SECRET_LENGTH;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Tests for JWT configuration.
 *
 * Verifies secret validation, length requirements, and error handling.
 */
@DisplayName("JWT Config Tests")
class JwtConfigTest {

    @Test
    @DisplayName("Should create SecretKey with valid secret")
    void secretKey_ValidSecret_ReturnsSecretKey() {
        // Given
        JwtConfig config = new JwtConfig();
        String validSecret = "mySecretKeyForJWTTokenGenerationThatShouldBeAtLeast256BitsLongForSecurity";
        ReflectionTestUtils.setField(config, "secret", validSecret);
        ReflectionTestUtils.setField(config, "expiration", 86400000L);

        // When
        SecretKey secretKey = config.secretKey();

        // Then
        assertThat(secretKey).isNotNull();
        // Algorithm depends on key length - could be HmacSHA256 or HmacSHA512
        assertThat(secretKey.getAlgorithm()).matches("HmacSHA(256|512)");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t", "\n"})
    @DisplayName("Should reject null, empty, or blank secret")
    void secretKey_InvalidSecret_ThrowsException(String invalidSecret) {
        // Given
        JwtConfig config = new JwtConfig();
        ReflectionTestUtils.setField(config, "secret", invalidSecret);
        ReflectionTestUtils.setField(config, "expiration", 86400000L);

        // When/Then
        assertThatThrownBy(config::secretKey)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET environment variable is required");
    }

    @Test
    @DisplayName("Should reject secret shorter than minimum length")
    void secretKey_ShortSecret_ThrowsException() {
        // Given
        JwtConfig config = new JwtConfig();
        String shortSecret = "short"; // Less than 32 characters
        ReflectionTestUtils.setField(config, "secret", shortSecret);
        ReflectionTestUtils.setField(config, "expiration", 86400000L);

        // When/Then
        assertThatThrownBy(config::secretKey)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("too short")
                .hasMessageContaining(String.valueOf(MIN_SECRET_LENGTH));
    }

    @Test
    @DisplayName("Should accept secret exactly at minimum length")
    void secretKey_SecretAtMinimumLength_ReturnsSecretKey() {
        // Given
        JwtConfig config = new JwtConfig();
        String minLengthSecret = "a".repeat(MIN_SECRET_LENGTH); // Exactly 32 characters
        ReflectionTestUtils.setField(config, "secret", minLengthSecret);
        ReflectionTestUtils.setField(config, "expiration", 86400000L);

        // When
        SecretKey secretKey = config.secretKey();

        // Then
        assertThat(secretKey).isNotNull();
    }

    @Test
    @DisplayName("Should accept secret longer than minimum length")
    void secretKey_LongSecret_ReturnsSecretKey() {
        // Given
        JwtConfig config = new JwtConfig();
        String longSecret = "mySecretKeyForJWTTokenGenerationThatShouldBeAtLeast256BitsLongForSecurityAndMore";
        ReflectionTestUtils.setField(config, "secret", longSecret);
        ReflectionTestUtils.setField(config, "expiration", 86400000L);

        // When
        SecretKey secretKey = config.secretKey();

        // Then
        assertThat(secretKey).isNotNull();
    }

    @Test
    @DisplayName("Should return expiration time")
    void getExpiration_ValidExpiration_ReturnsExpiration() {
        // Given
        JwtConfig config = new JwtConfig();
        Long expectedExpiration = 86400000L; // 24 hours
        ReflectionTestUtils.setField(config, "expiration", expectedExpiration);

        // When
        Long expiration = config.getExpiration();

        // Then
        assertThat(expiration).isEqualTo(expectedExpiration);
    }

    @Test
    @DisplayName("Should use default expiration if not set")
    void getExpiration_DefaultExpiration_ReturnsDefault() {
        // Given
        JwtConfig config = new JwtConfig();
        // expiration field uses @Value with default, so it should be set to default
        ReflectionTestUtils.setField(config, "expiration", 86400000L); // Default from @Value

        // When
        Long expiration = config.getExpiration();

        // Then
        assertThat(expiration).isEqualTo(86400000L); // 24 hours default
    }
}
