package com.huawei.gateway.service;

import com.huawei.common.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Objects;

/**
 * Token Revocation Service using Redis.
 *
 * Maintains a blacklist of revoked JWT tokens to enable:
 * - User logout (invalidate current session)
 * - Security response (invalidate compromised tokens)
 * - Password change (invalidate all user sessions)
 *
 * Tokens are stored with TTL matching their original expiry.
 */
@Service
public class TokenRevocationService {

    private static final Logger log = LoggerFactory.getLogger(TokenRevocationService.class);
    private static final String REVOKED_TOKEN_PREFIX = "revoked:token:";
    private static final String REVOKED_USER_PREFIX = "revoked:user:";

    private final ReactiveStringRedisTemplate redisTemplate;

    @Value("${jwt.expiration:86400000}")
    private long jwtExpirationMs;

    public TokenRevocationService(ReactiveStringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Revokes a specific token.
     *
     * @param tokenId             The JWT token ID (jti claim) or token hash
     * @param remainingTtlSeconds Time until token would naturally expire
     * @return Mono<Boolean> true if revoked successfully
     */
    public Mono<Boolean> revokeToken(String tokenId, long remainingTtlSeconds) {
        if (tokenId == null || tokenId.isBlank()) {
            return Mono.just(false);
        }

        String key = REVOKED_TOKEN_PREFIX + tokenId;
        Duration ttl = Objects.requireNonNull(Duration.ofSeconds(Math.max(remainingTtlSeconds, 1)));

        return redisTemplate.opsForValue()
                .set(Objects.requireNonNull(key), "revoked", ttl)
                .doOnSuccess(result -> log.info("Token revoked: {}", StringUtils.maskToken(tokenId)))
                .doOnError(e -> log.error("Failed to revoke token: {}", e.getMessage()));
    }

    /**
     * Revokes all tokens for a user (e.g., after password change).
     *
     * @param username The username whose tokens should be revoked
     * @return Mono<Boolean> true if revoked successfully
     */
    public Mono<Boolean> revokeAllUserTokens(String username) {
        if (username == null || username.isBlank()) {
            return Mono.just(false);
        }

        String key = REVOKED_USER_PREFIX + username;
        Duration ttl = Objects.requireNonNull(Duration.ofMillis(jwtExpirationMs));

        return redisTemplate.opsForValue()
                .set(Objects.requireNonNull(key), Objects.requireNonNull(String.valueOf(System.currentTimeMillis())), ttl)
                .doOnSuccess(result -> log.info("All tokens revoked for user: {}", username))
                .doOnError(e -> log.error("Failed to revoke user tokens: {}", e.getMessage()));
    }

    /**
     * Checks if a specific token is revoked.
     *
     * @param tokenId The JWT token ID (jti claim) or token hash
     * @return Mono<Boolean> true if token is revoked
     */
    public Mono<Boolean> isTokenRevoked(String tokenId) {
        if (tokenId == null || tokenId.isBlank()) {
            return Mono.just(false);
        }

        String key = REVOKED_TOKEN_PREFIX + tokenId;
        return redisTemplate.hasKey(key)
                .doOnNext(revoked -> {
                    if (Boolean.TRUE.equals(revoked)) {
                        log.debug("Token is revoked: {}", StringUtils.maskToken(tokenId));
                    }
                });
    }

    /**
     * Checks if all tokens for a user were revoked after a given timestamp.
     *
     * @param username      The username to check
     * @param tokenIssuedAt When the token was issued (epoch millis)
     * @return Mono<Boolean> true if user's tokens were revoked after token was
     *         issued
     */
    public Mono<Boolean> isUserTokensRevokedAfter(String username, long tokenIssuedAt) {
        if (username == null || username.isBlank()) {
            return Mono.just(false);
        }

        String key = REVOKED_USER_PREFIX + username;
        return redisTemplate.opsForValue().get(key)
                .map(revokedAt -> {
                    try {
                        long revokedTimestamp = Long.parseLong(revokedAt);
                        return revokedTimestamp > tokenIssuedAt;
                    } catch (NumberFormatException e) {
                        return false;
                    }
                })
                .defaultIfEmpty(false);
    }

    /**
     * Combined check: is this specific token OR the user's tokens revoked?
     */
    public Mono<Boolean> isRevoked(String tokenId, String username, long tokenIssuedAt) {
        return isTokenRevoked(tokenId)
                .flatMap(tokenRevoked -> {
                    if (Boolean.TRUE.equals(tokenRevoked)) {
                        return Mono.just(true);
                    }
                    return isUserTokensRevokedAfter(username, tokenIssuedAt);
                });
    }
}
