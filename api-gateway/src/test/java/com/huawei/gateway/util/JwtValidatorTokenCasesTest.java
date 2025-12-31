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
        validator = new JwtValidator(SECRET, false);
    }

    private String generateToken(String subject, Date expiration, SecretKey signingKey) {
        return Jwts.builder()
                .subject(subject)
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
    @DisplayName("Expired token should be rejected")
    void expiredToken_ReturnsInvalid() {
        Date past = new Date(System.currentTimeMillis() - 60_000); // 1 minute ago
        String token = generateToken("user1", past, secretKey);

        JwtValidator.ValidationResult result = validator.validateToken(token);
        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrorMessage()).contains("expired");
    }

    @Test
    @DisplayName("Token signed with different secret should fail signature check")
    void invalidSignature_ReturnsInvalid() {
        SecretKey otherKey = Keys.hmacShaKeyFor("anotherDifferentSecretKeyThatIsAlsoLongEnoughForHS256".getBytes(StandardCharsets.UTF_8));
        Date future = new Date(System.currentTimeMillis() + 60_000);
        String token = generateToken("user1", future, otherKey);

        JwtValidator.ValidationResult result = validator.validateToken(token);
        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrorMessage()).contains("signature");
    }
}
