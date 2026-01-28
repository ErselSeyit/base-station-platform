package com.huawei.gateway.util;

import java.util.Date;
import java.util.Optional;
import java.util.function.Function;

import javax.crypto.SecretKey;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * JWT Token Validator for API Gateway.
 * 
 * <p>This component validates JWT tokens in the gateway before forwarding requests
 * to downstream services. It uses the same secret key as the auth-service to verify
 * token signatures.
 * 
 * <p>Security Note: In production, consider:
 * - Using a shared secret key via configuration service (e.g., Vault, AWS Secrets Manager)
 * - Or validating tokens by calling auth-service (service-to-service validation)
 * - Implementing token revocation checking (blacklist/Redis)
 * - Adding rate limiting per token
 * 
 * <p>Current Implementation:
 * - Validates token signature using shared secret
 * - Checks token expiration
 * - Extracts claims for downstream services
 */
@Component
public class JwtValidator {

    private static final Logger log = LoggerFactory.getLogger(JwtValidator.class);
    
    private final SecretKey secretKey;
    private final boolean simulateValidation;

    /**
     * Creates JWT validator with secret key from configuration.
     * 
     * @param jwtSecret JWT secret key (must be at least 256 bits)
     * @param simulateValidation If true, simulates validation for testing (when tokens unavailable)
     */
    public JwtValidator(
            @Value("${jwt.secret:}") String jwtSecret,
            @Value("${jwt.simulate-validation:false}") boolean simulateValidation) {
        this.simulateValidation = simulateValidation;

        if (jwtSecret == null || jwtSecret.isBlank()) {
            if (!simulateValidation) {
                throw new IllegalStateException(
                    "JWT_SECRET is required but not configured. Set JWT_SECRET environment variable or enable simulation mode for testing."
                );
            }
            log.warn("SECURITY WARNING: JWT secret not configured. Token validation will be simulated. DO NOT USE IN PRODUCTION.");
            this.secretKey = null;
        } else {
            // Validate secret length (should be at least 32 characters for HS256)
            if (jwtSecret.length() < 32) {
                throw new IllegalStateException(
                    String.format("JWT secret is too short (%d chars). Must be at least 32 characters for security. Generate with: openssl rand -base64 64",
                        jwtSecret.length())
                );
            }
            this.secretKey = Keys.hmacShaKeyFor(jwtSecret.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }

        if (simulateValidation) {
            log.warn("***** SECURITY WARNING *****");
            log.warn("JWT validation is running in SIMULATION MODE");
            log.warn("This mode bypasses actual token validation and should NEVER be used in production");
            log.warn("Set jwt.simulate-validation=false for production deployment");
            log.warn("****************************");
        }
    }

    /**
     * Validates a JWT token.
     *
     * @param token the JWT token string
     * @return ValidationResult containing validation status and claims if valid
     */
    public ValidationResult validateToken(String token) {
        if (token == null || token.isBlank()) {
            return ValidationResult.invalid("Token is null or empty");
        }

        if (simulateValidation || secretKey == null) {
            return validateTokenInSimulationMode(token);
        }

        return validateTokenWithSignature(token);
    }

    private ValidationResult validateTokenInSimulationMode(String token) {
        log.warn("SECURITY RISK: Token validation running in simulation mode for token: {}...",
                token.length() > 10 ? token.substring(0, 10) : token);

        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            return ValidationResult.invalid("Invalid token format (not a JWT)");
        }

        // Parse token WITHOUT verification by creating an unsigned JWT with same payload
        try {
            // Create an unsigned JWT by replacing the header with {alg:none} and removing signature
            // Format: header.payload. (note the trailing dot with no signature)
            Claims claims = Jwts.parser()
                    .unsecured()
                    .build()
                    .parseUnsecuredClaims("eyJhbGciOiJub25lIn0." + parts[1] + ".")
                    .getPayload();

            log.warn("SECURITY RISK: Accepting token without signature verification (simulation mode). User: {}", claims.getSubject());
            return ValidationResult.valid(claims);
        } catch (Exception e) {
            log.error("Failed to parse token in simulation mode: {}", e.getMessage());
            return ValidationResult.invalid("Failed to parse token: " + e.getMessage());
        }
    }

    private ValidationResult validateTokenWithSignature(String token) {
        try {
            Claims claims = extractAllClaims(token);
            return validateClaims(claims);
        } catch (io.jsonwebtoken.security.SecurityException e) {
            log.warn("Token signature validation failed: {}", e.getMessage());
            return ValidationResult.invalid("Invalid token signature");
        } catch (io.jsonwebtoken.ExpiredJwtException e) {
            log.warn("Token expired: {}", e.getMessage());
            return ValidationResult.invalid("Token has expired");
        } catch (io.jsonwebtoken.MalformedJwtException e) {
            log.warn("Malformed token: {}", e.getMessage());
            return ValidationResult.invalid("Malformed token");
        } catch (Exception e) {
            log.error("Unexpected error validating token: {}", e.getMessage(), e);
            return ValidationResult.invalid("Token validation failed: " + e.getMessage());
        }
    }

    private ValidationResult validateClaims(Claims claims) {
        Date expiration = claims.getExpiration();
        if (expiration != null && expiration.before(new Date())) {
            return ValidationResult.invalid("Token has expired");
        }

        String username = claims.getSubject();
        if (username == null || username.isBlank()) {
            return ValidationResult.invalid("Token missing subject (username)");
        }

        log.debug("Token validated successfully for user: {}", username);
        return ValidationResult.valid(claims);
    }

    /**
     * Extracts all claims from a token.
     * 
     * @param token the JWT token
     * @return Claims object
     * @throws Exception if token is invalid
     */
    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Extracts a specific claim from a token.
     * 
     * @param token the JWT token
     * @param claimsResolver function to extract the claim
     * @return the claim value
     */
    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    /**
     * Result of token validation.
     */
    public static class ValidationResult {
        private final boolean valid;
        private final String errorMessage;
        private final Claims claims;

        private ValidationResult(boolean valid, String errorMessage, Claims claims) {
            this.valid = valid;
            this.errorMessage = errorMessage;
            this.claims = claims;
        }

        public static ValidationResult valid(Claims claims) {
            return new ValidationResult(true, null, claims);
        }

        public static ValidationResult valid(String message) {
            return new ValidationResult(true, message, null);
        }

        public static ValidationResult invalid(String errorMessage) {
            return new ValidationResult(false, errorMessage, null);
        }

        public boolean isValid() {
            return valid;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public Claims getClaims() {
            return claims;
        }

        public String getUsername() {
            return Optional.ofNullable(claims)
                    .map(Claims::getSubject)
                    .orElse(null);
        }

        public String getRole() {
            return Optional.ofNullable(claims)
                    .map(c -> c.get("role"))
                    .map(Object::toString)
                    .orElse(null);
        }
    }
}
