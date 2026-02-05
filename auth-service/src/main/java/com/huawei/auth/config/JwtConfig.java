package com.huawei.auth.config;

import com.huawei.common.constants.SecurityConstants;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

/**
 * JWT Configuration.
 * 
 * <p>Security: JWT secret must be provided via environment variable JWT_SECRET.
 * No default secret is used in production to prevent security vulnerabilities.
 * 
 * <p>For local development, set JWT_SECRET environment variable:
 * <pre>
 * export JWT_SECRET="your-secret-key-at-least-32-characters-long-for-security"
 * </pre>
 */
@Configuration
public class JwtConfig {

    private static final Logger log = LoggerFactory.getLogger(JwtConfig.class);

    @Value("${jwt.secret:}")
    private String secret;

    @Value("${jwt.expiration:86400000}") // 24 hours default
    private Long expiration;

    @Bean
    public SecretKey secretKey() {
        // Validate that secret is provided
        if (secret == null || secret.isBlank()) {
            String errorMsg = "JWT_SECRET environment variable is required but not set. " +
                    "Please set JWT_SECRET to a secure random string of at least " + SecurityConstants.MIN_SECRET_LENGTH + " characters.";
            log.error(errorMsg);
            throw new IllegalStateException(errorMsg);
        }

        // Validate secret length for security
        if (secret.length() < SecurityConstants.MIN_SECRET_LENGTH) {
            String errorMsg = String.format(
                    "JWT_SECRET is too short (%d characters). Must be at least %d characters for security.",
                    secret.length(), SecurityConstants.MIN_SECRET_LENGTH);
            log.error(errorMsg);
            throw new IllegalArgumentException(errorMsg);
        }

        log.info("JWT secret configured successfully (length: {} characters)", secret.length());
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public Long getExpiration() {
        return expiration;
    }
}

