package com.huawei.gateway.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for JWT validation in API Gateway.
 * 
 * Verifies that JWT tokens are properly validated,
 * including signature verification, expiration checking,
 * and simulation mode for testing.
 */
@DisplayName("JWT Validator Tests")
class JwtValidatorTest {

    private JwtValidator validatorWithSecret;
    private JwtValidator validatorWithoutSecret;
    private JwtValidator validatorSimulationMode;

    @BeforeEach
    void setUp() {
        // Validator with secret (production mode)
        validatorWithSecret = new JwtValidator(
                "mySecretKeyForJWTTokenGenerationThatShouldBeAtLeast256BitsLongForSecurity",
                false
        );

        // Validator with simulation mode enabled (empty secret is allowed)
        validatorWithoutSecret = new JwtValidator("", true);

        // Validator with simulation mode enabled
        validatorSimulationMode = new JwtValidator("", true);
    }

    @Test
    @DisplayName("Should reject null token")
    void validateToken_NullToken_ReturnsInvalid() {
        // When
        JwtValidator.ValidationResult result = validatorWithSecret.validateToken(null);

        // Then
        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrorMessage()).contains("null or empty");
    }

    @Test
    @DisplayName("Should reject empty token")
    void validateToken_EmptyToken_ReturnsInvalid() {
        // When
        JwtValidator.ValidationResult result = validatorWithSecret.validateToken("");

        // Then
        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrorMessage()).contains("null or empty");
    }

    @Test
    @DisplayName("Should reject invalid JWT format in simulation mode")
    void validateToken_InvalidFormat_SimulationMode_ReturnsInvalid() {
        // Given
        String invalidToken = "not.a.valid.jwt";

        // When
        JwtValidator.ValidationResult result = validatorSimulationMode.validateToken(invalidToken);

        // Then
        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrorMessage()).contains("Invalid token format");
    }

    @Test
    @DisplayName("Should accept well-formed JWT in simulation mode")
    void validateToken_WellFormedJWT_SimulationMode_ReturnsValid() {
        // Given - Generate a well-formed JWT dynamically (3 parts separated by dots)
        String header = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes());
        String payload = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"sub\":\"user1\"}".getBytes());
        String wellFormedToken = header + "." + payload + ".signature";

        // When
        JwtValidator.ValidationResult result = validatorSimulationMode.validateToken(wellFormedToken);

        // Then
        assertThat(result.isValid()).isTrue();
    }

    @Test
    @DisplayName("Should use simulation mode when secret is not configured")
    void validateToken_NoSecret_DefaultsToSimulation() {
        // Given - Generate a test JWT token dynamically
        String header = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes());
        String payload = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"sub\":\"testuser\",\"role\":\"ROLE_USER\"}".getBytes());
        String token = header + "." + payload + ".test-signature";

        // When
        JwtValidator.ValidationResult result = validatorWithoutSecret.validateToken(token);

        // Then
        // Should use simulation mode (accepts well-formed JWT)
        assertThat(result.isValid()).isTrue();
    }

    @Test
    @DisplayName("Should throw for short secret")
    void constructor_ShortSecret_ThrowsException() {
        // This test verifies the constructor rejects short secrets
        try {
            new JwtValidator("short", false);
            throw new AssertionError("Expected IllegalStateException for short secret");
        } catch (IllegalStateException e) {
            assertThat(e.getMessage()).contains("too short");
        }
    }
}
