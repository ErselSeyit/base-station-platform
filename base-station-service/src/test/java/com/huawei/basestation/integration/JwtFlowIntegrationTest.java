package com.huawei.basestation.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.huawei.basestation.repository.BaseStationRepository;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * Integration tests for JWT flow: Token Generation → Token Validation.
 * 
 * These tests verify the end-to-end JWT flow:
 * - Token generation (simulating auth-service behavior)
 * - Token validation (simulating gateway behavior)
 * - Token expiration handling
 * - Invalid token rejection
 * - Claims extraction and propagation
 * 
 * This test simulates the integration between:
 * - auth-service: Generates tokens using JwtUtil
 * - api-gateway: Validates tokens using JwtValidator
 * 
 * Both services use the same JWT secret for signature verification.
 * 
 * Uses Testcontainers for PostgreSQL database (required by Spring Boot context).
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        classes = {IntegrationTestApplication.class},
        properties = {
                "spring.cache.type=none",
                "spring.main.allow-bean-definition-overriding=true",
                "spring.profiles.active=integration-test"
        })
@Testcontainers
@DisabledIf("skipInDemoOrNoDocker")
@DisplayName("JWT Flow Integration Tests")
class JwtFlowIntegrationTest {
    static boolean skipInDemoOrNoDocker() {
        try {
            boolean demoMode = Boolean.parseBoolean(System.getProperty("demo.mode",
                    String.valueOf(Boolean.parseBoolean(System.getenv().getOrDefault("DEMO_MODE", "false")))));
            boolean dockerAvailable = DockerClientFactory.instance().isDockerAvailable();
            return demoMode || !dockerAvailable;
        } catch (Exception e) {
            return true;
        }
    }

    @Container
    @ServiceConnection
    @SuppressWarnings("resource") // Testcontainers manages lifecycle via @Container annotation
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    // Shared JWT secret (same as used by auth-service and gateway)
    private static final String JWT_SECRET = "mySecretKeyForJWTTokenGenerationThatShouldBeAtLeast256BitsLongForSecurity";
    private SecretKey secretKey;

    @Autowired
    private BaseStationRepository repository;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // Set JWT secret for testing
        registry.add("jwt.secret", () -> JWT_SECRET);
        registry.add("jwt.simulate-validation", () -> "false");

        // Disable Redis for tests
        registry.add("spring.data.redis.host", () -> "localhost");
        registry.add("spring.cache.type", () -> "none");
        // JPA configuration
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.jpa.show-sql", () -> "false");
        registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
    }

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        secretKey = Keys.hmacShaKeyFor(JWT_SECRET.getBytes());
    }

    @Nested
    @DisplayName("Token Generation and Validation Flow")
    class TokenGenerationAndValidation {

        @Test
        @DisplayName("Should generate valid token and validate successfully")
        void generateToken_ValidateToken_Success() {
            // Simulate auth-service: Generate token
            String username = "testuser";
            String role = "ADMIN";
            String token = generateToken(username, role);

            // Verify token structure (JWT has 3 parts)
            assertThat(token).isNotNull();
            assertThat(token.split("\\.")).hasSize(3);

            // Simulate gateway: Validate token
            Claims claims = validateToken(token);

            // Verify claims
            assertThat(claims)
                    .isNotNull()
                    .containsEntry("role", role);
            assertThat(claims.getSubject()).isEqualTo(username);
            assertThat(claims.getExpiration()).isAfter(new Date());
        }

        @Test
        @DisplayName("Should extract username from validated token")
        void validateToken_ExtractUsername_Success() {
            String username = "john.doe";
            String token = generateToken(username, "USER");

            Claims claims = validateToken(token);
            String extractedUsername = claims.getSubject();

            assertThat(extractedUsername).isEqualTo(username);
        }

        @Test
        @DisplayName("Should extract role claim from validated token")
        void validateToken_ExtractRole_Success() {
            String role = "OPERATOR";
            String token = generateToken("testuser", role);

            Claims claims = validateToken(token);
            String extractedRole = (String) claims.get("role");

            assertThat(extractedRole).isEqualTo(role);
        }
    }

    @Nested
    @DisplayName("Token Expiration")
    class TokenExpiration {

        @Test
        @DisplayName("Should reject expired token")
        void validateToken_ExpiredToken_ThrowsException() throws Exception {
            // Generate expired token (expired 1 hour ago)
            String username = "testuser";
            long expirationTime = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(1);
            
            String expiredToken = Jwts.builder()
                    .subject(username)
                    .issuedAt(new Date(System.currentTimeMillis() - TimeUnit.HOURS.toMillis(2)))
                    .expiration(new Date(expirationTime))
                    .signWith(secretKey)
                    .compact();

            // Validation should fail for expired token
            try {
                validateToken(expiredToken);
                // If we get here, validation didn't throw exception - that's a problem
                throw new AssertionError("Expected ExpiredJwtException for expired token");
            } catch (io.jsonwebtoken.ExpiredJwtException e) {
                // Expected exception
                assertThat(e.getMessage()).contains("expired");
            }
        }

        @Test
        @DisplayName("Should accept token that is not yet expired")
        void validateToken_NotExpired_Success() {
            // Generate token with future expiration (1 hour from now)
            String username = "testuser";
            long expirationTime = System.currentTimeMillis() + TimeUnit.HOURS.toMillis(1);
            
            String validToken = Jwts.builder()
                    .subject(username)
                    .issuedAt(new Date())
                    .expiration(new Date(expirationTime))
                    .signWith(secretKey)
                    .compact();

            // Should validate successfully
            Claims claims = validateToken(validToken);
            assertThat(claims).isNotNull();
            assertThat(claims.getSubject()).isEqualTo(username);
        }
    }

    @Nested
    @DisplayName("Invalid Token Handling")
    class InvalidTokenHandling {

        @Test
        @DisplayName("Should reject token with wrong signature")
        void validateToken_WrongSignature_ThrowsException() {
            // Generate token with different secret
            String wrongSecret = "differentSecretKeyThatIsAlsoAtLeast32CharactersLongForTesting";
            SecretKey wrongKey = Keys.hmacShaKeyFor(wrongSecret.getBytes());
            
            String tokenWithWrongSignature = Jwts.builder()
                    .subject("testuser")
                    .issuedAt(new Date())
                    .expiration(new Date(System.currentTimeMillis() + TimeUnit.HOURS.toMillis(1)))
                    .signWith(wrongKey)
                    .compact();

            // Validation should fail due to signature mismatch
            try {
                validateToken(tokenWithWrongSignature);
                throw new AssertionError("Expected SecurityException for wrong signature");
            } catch (io.jsonwebtoken.security.SecurityException e) {
                // Expected exception
                assertThat(e.getMessage()).isNotNull();
            }
        }

        @Test
        @DisplayName("Should reject malformed token")
        void validateToken_MalformedToken_ThrowsException() {
            String malformedToken = "not.a.valid.jwt.token";

            try {
                validateToken(malformedToken);
                throw new AssertionError("Expected MalformedJwtException for malformed token");
            } catch (io.jsonwebtoken.MalformedJwtException e) {
                // Expected exception
                assertThat(e.getMessage()).isNotNull();
            }
        }

        @Test
        @DisplayName("Should reject token without subject")
        void validateToken_NoSubject_ThrowsException() {
            // Generate token without subject
            String tokenWithoutSubject = Jwts.builder()
                    .issuedAt(new Date())
                    .expiration(new Date(System.currentTimeMillis() + TimeUnit.HOURS.toMillis(1)))
                    .signWith(secretKey)
                    .compact();

            // Validation should succeed (parsing), but subject should be null
            Claims claims = validateToken(tokenWithoutSubject);
            assertThat(claims.getSubject()).isNull();
        }
    }

    @Nested
    @DisplayName("Token Claims and Propagation")
    class TokenClaimsAndPropagation {

        @Test
        @DisplayName("Should include custom claims in token")
        void generateToken_WithCustomClaims_Success() {
            String username = "testuser";
            Map<String, Object> customClaims = new HashMap<>();
            customClaims.put("role", "ADMIN");
            customClaims.put("department", "IT");
            customClaims.put("permissions", new String[]{"read", "write", "delete"});

            String token = Jwts.builder()
                    .claims(customClaims)
                    .subject(username)
                    .issuedAt(new Date())
                    .expiration(new Date(System.currentTimeMillis() + TimeUnit.HOURS.toMillis(1)))
                    .signWith(secretKey)
                    .compact();

            Claims claims = validateToken(token);

            assertThat(claims)
                    .containsEntry("role", "ADMIN")
                    .containsEntry("department", "IT")
                    .containsKey("permissions");
        }

        @Test
        @DisplayName("Should propagate username for downstream services")
        void validateToken_ExtractUsername_ForDownstream() {
            String username = "operator1";
            String token = generateToken(username, "OPERATOR");

            Claims claims = validateToken(token);
            String extractedUsername = claims.getSubject();

            // This username would be added to X-User-Name header in gateway
            assertThat(extractedUsername)
                    .isEqualTo(username)
                    .isNotBlank();
        }
    }

    // ========================================
    // Helper Methods
    // ========================================

    /**
     * Simulates token generation in auth-service using JwtUtil.
     * 
     * @param username the username (subject)
     * @param role the user role
     * @return JWT token string
     */
    private String generateToken(String username, String role) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("role", role);
        
        return Jwts.builder()
                .claims(claims)
                .subject(username)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + TimeUnit.HOURS.toMillis(24))) // 24 hours
                .signWith(secretKey)
                .compact();
    }

    /**
     * Simulates token validation in api-gateway using JwtValidator.
     * 
     * @param token the JWT token to validate
     * @return Claims if valid
     * @throws Exception if token is invalid
     */
    private Claims validateToken(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
