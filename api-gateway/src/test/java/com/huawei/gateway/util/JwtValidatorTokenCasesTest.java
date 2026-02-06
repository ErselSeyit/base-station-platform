package com.huawei.gateway.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@DisplayName("JWT Validator Token Cases")
class JwtValidatorTokenCasesTest {

    private static final String SECRET = "mySecretKeyForJWTTokenGenerationThatShouldBeAtLeast256BitsLongForSecurity";
    private SecretKey secretKey;
    private JwtValidator validator;

    @BeforeEach
    void setUp() {
        secretKey = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        validator = new JwtValidator(SECRET);
    }

    private String generateToken(String subject, Date expiration, SecretKey signingKey) {
        return Jwts.builder()
                .subject(subject)
                .expiration(expiration)
                .signWith(signingKey)
                .compact();
    }

    private String generateTokenWithRole(String subject, String role, Date expiration, SecretKey signingKey) {
        return Jwts.builder()
                .subject(subject)
                .claim("role", role)
                .expiration(expiration)
                .signWith(signingKey)
                .compact();
    }

    @Test
    @DisplayName("Valid token should pass and expose claims")
    void validToken_PassesValidation() {
        Date future = new Date(System.currentTimeMillis() + 60_000); // 1 minute ahead
        String token = generateToken("user1", future, secretKey);

        JwtValidator.ValidationResult result = validator.validateToken(token);
        assertThat(result.isValid()).isTrue();
        Claims claims = result.getClaims();
        assertThat(claims).isNotNull();
        assertThat(claims.getSubject()).isEqualTo("user1");
    }

    @Test
    @DisplayName("Valid token with role should expose role claim")
    void validTokenWithRole_ExposesRole() {
        Date future = new Date(System.currentTimeMillis() + 60_000);
        String token = generateTokenWithRole("admin", "ROLE_ADMIN", future, secretKey);

        JwtValidator.ValidationResult result = validator.validateToken(token);
        assertThat(result.isValid()).isTrue();
        assertThat(result.getUsername()).isEqualTo("admin");
        assertThat(result.getRole()).isEqualTo("ROLE_ADMIN");
    }

    @Test
    @DisplayName("Expired token should be rejected")
    void expiredToken_ReturnsInvalid() {
        Date past = new Date(System.currentTimeMillis() - 60_000); // 1 minute ago
        String token = generateToken("user1", past, secretKey);

        JwtValidator.ValidationResult result = validator.validateToken(token);
        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrorMessage()).containsIgnoringCase("expired");
    }

    @Test
    @DisplayName("Token signed with different secret should fail signature check")
    void invalidSignature_ReturnsInvalid() {
        SecretKey otherKey = Keys.hmacShaKeyFor(
                "anotherDifferentSecretKeyThatIsAlsoLongEnoughForHS256".getBytes(StandardCharsets.UTF_8));
        Date future = new Date(System.currentTimeMillis() + 60_000);
        String token = generateToken("user1", future, otherKey);

        JwtValidator.ValidationResult result = validator.validateToken(token);
        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrorMessage()).containsIgnoringCase("signature");
    }

    @Test
    @DisplayName("Token without subject should fail validation")
    void tokenWithoutSubject_ReturnsInvalid() {
        Date future = new Date(System.currentTimeMillis() + 60_000);
        String token = Jwts.builder()
                .expiration(future)
                .signWith(secretKey)
                .compact();

        JwtValidator.ValidationResult result = validator.validateToken(token);
        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrorMessage()).containsIgnoringCase("subject");
    }

    @Test
    @DisplayName("getIssuedAt returns 0 for token without iat claim")
    void tokenWithoutIssuedAt_ReturnsZero() {
        Date future = new Date(System.currentTimeMillis() + 60_000);
        String token = generateToken("user1", future, secretKey);

        JwtValidator.ValidationResult result = validator.validateToken(token);
        assertThat(result.isValid()).isTrue();
        // No iat claim in this token, should return 0
        assertThat(result.getIssuedAt()).isZero();
    }

    @Test
    @DisplayName("getTokenId returns null for token without jti claim")
    void tokenWithoutJti_ReturnsNull() {
        Date future = new Date(System.currentTimeMillis() + 60_000);
        String token = generateToken("user1", future, secretKey);

        JwtValidator.ValidationResult result = validator.validateToken(token);
        assertThat(result.isValid()).isTrue();
        assertThat(result.getTokenId()).isNull();
    }
}
