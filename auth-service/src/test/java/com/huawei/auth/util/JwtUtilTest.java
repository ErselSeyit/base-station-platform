package com.huawei.auth.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Date;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.huawei.auth.config.JwtConfig;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * Tests for JWT utility class.
 * 
 * Verifies token generation, extraction, validation, and expiration handling.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("JWT Util Tests")
class JwtUtilTest {

    @Mock
    private JwtConfig jwtConfig;

    private JwtUtil jwtUtil;

    private SecretKey secretKey;
    private static final String TEST_SECRET = "mySecretKeyForJWTTokenGenerationThatShouldBeAtLeast256BitsLongForSecurity";
    private static final long EXPIRATION_MS = 3600000L; // 1 hour

    @BeforeEach
    void setUp() {
        secretKey = Keys.hmacShaKeyFor(TEST_SECRET.getBytes());
        lenient().when(jwtConfig.getExpiration()).thenReturn(EXPIRATION_MS);
        jwtUtil = new JwtUtil(jwtConfig, secretKey);
    }

    @Test
    @DisplayName("Should generate valid token with username and role")
    void generateToken_ValidInput_ReturnsValidToken() {
        // Given
        String username = "testuser";
        String role = "ADMIN";

        // When
        String token = jwtUtil.generateToken(username, role);

        // Then
        assertThat(token).isNotNull();
        assertThat(token.split("\\.")).hasSize(3); // JWT has 3 parts
    }

    @Test
    @DisplayName("Should extract username from token")
    void extractUsername_ValidToken_ReturnsUsername() {
        // Given
        String username = "testuser";
        String token = jwtUtil.generateToken(username, "USER");

        // When
        String extractedUsername = jwtUtil.extractUsername(token);

        // Then
        assertThat(extractedUsername).isEqualTo(username);
    }

    @Test
    @DisplayName("Should extract expiration date from token")
    void extractExpiration_ValidToken_ReturnsExpirationDate() {
        // Given
        String username = "testuser";
        String token = jwtUtil.generateToken(username, "USER");

        // When
        Date expiration = jwtUtil.extractExpiration(token);

        // Then
        assertThat(expiration)
                .isNotNull()
                .isAfter(new Date());
    }

    @Test
    @DisplayName("Should extract role claim from token")
    void extractClaim_Role_ReturnsRole() {
        // Given
        String username = "testuser";
        String role = "ADMIN";
        String token = jwtUtil.generateToken(username, role);

        // When
        String extractedRole = jwtUtil.extractClaim(token, claims -> claims.get("role", String.class));

        // Then
        assertThat(extractedRole).isEqualTo(role);
    }

    @Test
    @DisplayName("Should validate token with correct username")
    void validateToken_CorrectUsername_ReturnsTrue() {
        // Given
        String username = "testuser";
        String token = jwtUtil.generateToken(username, "USER");

        // When
        Boolean isValid = jwtUtil.validateToken(token, username);

        // Then
        assertThat(isValid).isTrue();
    }

    @Test
    @DisplayName("Should reject token with incorrect username")
    void validateToken_IncorrectUsername_ReturnsFalse() {
        // Given
        String username = "testuser";
        String wrongUsername = "wronguser";
        String token = jwtUtil.generateToken(username, "USER");

        // When
        Boolean isValid = jwtUtil.validateToken(token, wrongUsername);

        // Then
        assertThat(isValid).isFalse();
    }

    @Test
    @DisplayName("Should detect expired token")
    void isTokenExpired_ExpiredToken_ReturnsTrue() {
        // Given - Create a token with very short expiration
        when(jwtConfig.getExpiration()).thenReturn(100L); // 100ms

        String username = "testuser";
        String token = jwtUtil.generateToken(username, "USER");

        // Wait for token to expire
        await().atMost(Duration.ofMillis(500))
                .pollDelay(Duration.ofMillis(200))
                .untilAsserted(() -> {
                    // When/Then - Expired token throws exception when extracting expiration
                    assertThatThrownBy(() -> jwtUtil.isTokenExpired(token))
                            .isInstanceOf(io.jsonwebtoken.ExpiredJwtException.class);
                });
    }

    @Test
    @DisplayName("Should detect non-expired token")
    void isTokenExpired_ValidToken_ReturnsFalse() {
        // Given
        lenient().when(jwtConfig.getExpiration()).thenReturn(EXPIRATION_MS);
        String username = "testuser";
        String token = jwtUtil.generateToken(username, "USER");

        // When
        Boolean isExpired = jwtUtil.isTokenExpired(token);

        // Then
        assertThat(isExpired).isFalse();
    }

    @Test
    @DisplayName("Should reject expired token during validation")
    void validateToken_ExpiredToken_ReturnsFalse() {
        // Given - Create a token with very short expiration
        when(jwtConfig.getExpiration()).thenReturn(100L); // 100ms

        String username = "testuser";
        String token = jwtUtil.generateToken(username, "USER");

        // Wait for token to expire
        await().atMost(Duration.ofMillis(500))
                .pollDelay(Duration.ofMillis(200))
                .untilAsserted(() -> {
                    // When - Expired token throws exception during validation
                    assertThatThrownBy(() -> jwtUtil.validateToken(token, username))
                            .isInstanceOf(Exception.class);
                });
    }

    @Test
    @DisplayName("Should reject invalid token format")
    void extractUsername_InvalidToken_ThrowsException() {
        // Given
        String invalidToken = "not.a.valid.jwt.token";

        // When/Then
        assertThatThrownBy(() -> jwtUtil.extractUsername(invalidToken))
                .isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("Should reject token with wrong signature")
    void extractUsername_TokenWithWrongSecret_ThrowsException() {
        // Given - Create token with different secret
        SecretKey wrongSecret = Keys.hmacShaKeyFor("differentSecretKeyThatShouldBeAtLeast256BitsLongForSecurity".getBytes());
        String token = Jwts.builder()
                .subject("testuser")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + EXPIRATION_MS))
                .signWith(wrongSecret)
                .compact();

        // When/Then - Should throw exception due to signature mismatch
        assertThatThrownBy(() -> jwtUtil.extractUsername(token))
                .isInstanceOf(io.jsonwebtoken.security.SecurityException.class);
    }

    @Test
    @DisplayName("Should generate valid tokens for same user")
    void generateToken_SameUser_ValidTokens() {
        // Given
        String username = "testuser";
        String role = "USER";

        // When
        String token1 = jwtUtil.generateToken(username, role);
        await().pollDelay(Duration.ofMillis(10)).until(() -> true); // Small delay to ensure different issuedAt
        String token2 = jwtUtil.generateToken(username, role);

        // Then - Both should be valid (may or may not be equal depending on timing)
        assertThat(jwtUtil.validateToken(token1, username)).isTrue();
        assertThat(jwtUtil.validateToken(token2, username)).isTrue();
        // Both should extract same username
        assertThat(jwtUtil.extractUsername(token1)).isEqualTo(username);
        assertThat(jwtUtil.extractUsername(token2)).isEqualTo(username);
    }

    @Test
    @DisplayName("Should extract all claims correctly")
    void extractAllClaims_ValidToken_ReturnsAllClaims() {
        // Given
        String username = "testuser";
        String role = "ADMIN";
        String token = jwtUtil.generateToken(username, role);

        // When
        String extractedUsername = jwtUtil.extractUsername(token);
        String extractedRole = jwtUtil.extractClaim(token, claims -> claims.get("role", String.class));
        Date issuedAt = jwtUtil.extractClaim(token, Claims::getIssuedAt);
        Date expiration = jwtUtil.extractExpiration(token);

        // Then
        assertThat(extractedUsername).isEqualTo(username);
        assertThat(extractedRole).isEqualTo(role);
        assertThat(issuedAt).isNotNull();
        assertThat(expiration)
                .isNotNull()
                .isAfter(issuedAt);
    }
}
