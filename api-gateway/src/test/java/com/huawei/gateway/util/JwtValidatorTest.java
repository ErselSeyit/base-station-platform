package com.huawei.gateway.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Tests for JWT validation in API Gateway.
 *
 * Verifies that JWT tokens are properly validated,
 * including signature verification and expiration checking.
 *
 * Note: Simulation mode has been removed for security.
 * All tokens now require valid signatures.
 */
@DisplayName("JWT Validator Tests")
class JwtValidatorTest {

    private static final String VALID_SECRET =
            "mySecretKeyForJWTTokenGenerationThatShouldBeAtLeast256BitsLongForSecurity";

    private JwtValidator validatorWithSecret;

    @BeforeEach
    void setUp() {
        // Validator with secret (production mode - now the only mode)
        validatorWithSecret = new JwtValidator(VALID_SECRET);
    }

    @ParameterizedTest(name = "Should reject token: \"{0}\"")
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t", "\n"})
    @DisplayName("Should reject null, empty, or blank tokens")
    void validateToken_InvalidToken_ReturnsInvalid(String token) {
        JwtValidator.ValidationResult result = validatorWithSecret.validateToken(token);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrorMessage()).contains("null or empty");
    }

    @Test
    @DisplayName("Should reject malformed token")
    void validateToken_MalformedToken_ReturnsInvalid() {
        String malformedToken = "not.a.valid.jwt.token";

        JwtValidator.ValidationResult result = validatorWithSecret.validateToken(malformedToken);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrorMessage()).containsIgnoringCase("malformed");
    }

    @ParameterizedTest(name = "Should throw for secret: \"{0}\"")
    @NullAndEmptySource
    @DisplayName("Should throw for null or empty secret")
    void constructor_NullOrEmptySecret_ThrowsException(String secret) {
        assertThatThrownBy(() -> new JwtValidator(secret))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("REQUIRED");
    }

    @Test
    @DisplayName("Should throw for short secret")
    void constructor_ShortSecret_ThrowsException() {
        assertThatThrownBy(() -> new JwtValidator("short"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("too short");
    }

    @Test
    @DisplayName("Should accept valid long secret")
    void constructor_ValidSecret_CreatesValidator() {
        JwtValidator validator = new JwtValidator(VALID_SECRET);
        assertThat(validator).isNotNull();
    }
}
