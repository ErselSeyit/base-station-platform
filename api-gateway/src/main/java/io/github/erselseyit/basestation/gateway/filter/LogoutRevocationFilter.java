package io.github.erselseyit.basestation.gateway.filter;

import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import io.github.erselseyit.basestation.common.security.AuthConstants;
import io.github.erselseyit.basestation.gateway.service.TokenRevocationService;

import reactor.core.publisher.Mono;

/**
 * Blacklists the presented JWT when a user logs out, so it can no longer be used
 * even before it expires. Runs at the gateway (which owns the revocation store),
 * revokes the token, then forwards the logout to auth-service (which clears the
 * cookie and revokes the refresh token). Together with the revocation check in
 * {@link JwtAuthenticationFilter}, this makes logout actually invalidate a JWT.
 */
@Component
public class LogoutRevocationFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(LogoutRevocationFilter.class);
    private static final String LOGOUT_PATH = "/api/v1/auth/logout";

    private final TokenRevocationService revocationService;

    /** Blacklist entries live for at least a token lifetime, so a revoked token stays revoked until it would expire anyway. */
    @Value("${jwt.expiration:86400000}")
    private long jwtExpirationMs;

    public LogoutRevocationFilter(TokenRevocationService revocationService) {
        this.revocationService = revocationService;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        if (!LOGOUT_PATH.equals(request.getURI().getPath())
                || !HttpMethod.POST.equals(request.getMethod())) {
            return chain.filter(exchange);
        }

        return extractToken(request)
                .map(token -> revocationService
                        .revokeToken(JwtAuthenticationFilter.tokenKey(token), jwtExpirationMs / 1000)
                        .onErrorResume(e -> {
                            log.warn("Failed to blacklist token on logout: {}", e.getMessage());
                            return Mono.just(false);
                        })
                        .then(chain.filter(exchange)))
                .orElseGet(() -> chain.filter(exchange));
    }

    private Optional<String> extractToken(ServerHttpRequest request) {
        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader != null && authHeader.startsWith(AuthConstants.BEARER_PREFIX)) {
            String token = authHeader.substring(AuthConstants.BEARER_PREFIX_LENGTH);
            if (!token.isBlank()) {
                return Optional.of(token);
            }
        }
        List<HttpCookie> cookies = request.getCookies().get(AuthConstants.AUTH_COOKIE_NAME);
        if (cookies != null && !cookies.isEmpty()) {
            String token = cookies.get(0).getValue();
            if (!token.isBlank()) {
                return Optional.of(token);
            }
        }
        return Optional.empty();
    }

    @Override
    public int getOrder() {
        // Run before routing so the revocation is written before auth-service
        // handles the logout.
        return Ordered.HIGHEST_PRECEDENCE + 100;
    }
}
