package com.huawei.gateway.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.InetSocketAddress;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.util.ReflectionTestUtils;

import com.huawei.gateway.util.JwtValidator;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Unit tests for JwtAuthenticationFilter.
 *
 * Tests cover:
 * - Public endpoint bypass
 * - Token extraction and validation
 * - User header injection
 * - Actuator IP-based access control
 * - Error handling
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("JwtAuthenticationFilter Tests")
@SuppressWarnings("null")
class JwtAuthenticationFilterTest {

    @Mock
    private JwtValidator jwtValidator;

    @Mock
    private GatewayFilterChain filterChain;

    private JwtAuthenticationFilter filter;
    private GatewayFilter gatewayFilter;

    private static final String VALID_TOKEN = "eyJhbGciOiJIUzI1NiJ9.valid.token";
    private static final String INTERNAL_SECRET = "test-internal-secret-for-hmac-signing-minimum-32-chars";

    @BeforeEach
    void setUp() {
        filter = new JwtAuthenticationFilter(jwtValidator);
        ReflectionTestUtils.setField(filter, "internalSecret", INTERNAL_SECRET);
        ReflectionTestUtils.setField(filter, "actuatorAllowedIps", "127.0.0.1,::1,10.0.0.0/8,172.16.0.0/12,192.168.0.0/16");
        gatewayFilter = filter.apply(new JwtAuthenticationFilter.Config());
    }

    @Nested
    @DisplayName("Public Endpoints")
    class PublicEndpointTests {

        @ParameterizedTest(name = "Should skip validation for: {0}")
        @ValueSource(strings = {
                "/api/v1/auth/login",
                "/api/v1/auth/register",
                "/api/v1/auth/logout",
                "/swagger-ui/index.html",
                "/v3/api-docs"
        })
        @DisplayName("Should skip JWT validation for public endpoints")
        void apply_PublicEndpoint_SkipsValidation(String path) {
            // Given
            MockServerHttpRequest request = MockServerHttpRequest
                    .get(path)
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);

            when(filterChain.filter(exchange)).thenReturn(Mono.empty());

            // When
            Mono<Void> result = gatewayFilter.filter(exchange, filterChain);

            // Then
            StepVerifier.create(result).verifyComplete();
            verify(filterChain).filter(exchange);
            verify(jwtValidator, never()).validateToken(anyString());
        }
    }

    @Nested
    @DisplayName("Token Validation")
    class TokenValidationTests {

        @Test
        @DisplayName("Should pass request with valid token and add user headers")
        void apply_ValidToken_PassesRequestWithHeaders() {
            // Given
            String path = "/api/v1/stations";
            String username = "testuser";
            String role = "ADMIN";

            MockServerHttpRequest request = MockServerHttpRequest
                    .get(path)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + VALID_TOKEN)
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);

            JwtValidator.ValidationResult validResult = createValidResult(username, role);
            when(jwtValidator.validateToken(VALID_TOKEN)).thenReturn(validResult);
            when(filterChain.filter(org.mockito.ArgumentMatchers.any())).thenReturn(Mono.empty());

            // When
            Mono<Void> result = gatewayFilter.filter(exchange, filterChain);

            // Then
            StepVerifier.create(result).verifyComplete();
            verify(jwtValidator).validateToken(VALID_TOKEN);
            verify(filterChain).filter(org.mockito.ArgumentMatchers.any());
        }

        @Test
        @DisplayName("Should return 401 when Authorization header is missing")
        void apply_MissingAuthHeader_Returns401() {
            // Given
            String path = "/api/v1/stations";

            MockServerHttpRequest request = MockServerHttpRequest
                    .get(path)
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);

            // When
            Mono<Void> result = gatewayFilter.filter(exchange, filterChain);

            // Then
            StepVerifier.create(result).verifyComplete();
            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            verify(jwtValidator, never()).validateToken(anyString());
        }

        @Test
        @DisplayName("Should return 401 when token is invalid")
        void apply_InvalidToken_Returns401() {
            // Given
            String path = "/api/v1/stations";
            String invalidToken = "invalid.token";

            MockServerHttpRequest request = MockServerHttpRequest
                    .get(path)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + invalidToken)
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);

            JwtValidator.ValidationResult invalidResult = createInvalidResult("Token expired");
            when(jwtValidator.validateToken(invalidToken)).thenReturn(invalidResult);

            // When
            Mono<Void> result = gatewayFilter.filter(exchange, filterChain);

            // Then
            StepVerifier.create(result).verifyComplete();
            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @DisplayName("Should return 401 when Bearer prefix is missing")
        void apply_NoBearerPrefix_Returns401() {
            // Given
            String path = "/api/v1/stations";

            MockServerHttpRequest request = MockServerHttpRequest
                    .get(path)
                    .header(HttpHeaders.AUTHORIZATION, VALID_TOKEN) // No "Bearer " prefix
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);

            // When
            Mono<Void> result = gatewayFilter.filter(exchange, filterChain);

            // Then
            StepVerifier.create(result).verifyComplete();
            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @DisplayName("Should return 401 when token is empty after Bearer prefix")
        void apply_EmptyToken_Returns401() {
            // Given
            String path = "/api/v1/stations";

            MockServerHttpRequest request = MockServerHttpRequest
                    .get(path)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer ")
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);

            // When
            Mono<Void> result = gatewayFilter.filter(exchange, filterChain);

            // Then
            StepVerifier.create(result).verifyComplete();
            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @DisplayName("Should return 401 when token is blank")
        void apply_BlankToken_Returns401() {
            // Given
            String path = "/api/v1/stations";

            MockServerHttpRequest request = MockServerHttpRequest
                    .get(path)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer    ")
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);

            // When
            Mono<Void> result = gatewayFilter.filter(exchange, filterChain);

            // Then
            StepVerifier.create(result).verifyComplete();
            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    @Nested
    @DisplayName("Actuator Endpoint Access Control")
    class ActuatorAccessControlTests {

        @Test
        @DisplayName("Should allow actuator access from localhost")
        void apply_ActuatorFromLocalhost_Allows() {
            // Given
            MockServerHttpRequest request = MockServerHttpRequest
                    .get("/actuator/health")
                    .remoteAddress(new InetSocketAddress("127.0.0.1", 12345))
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);

            when(filterChain.filter(exchange)).thenReturn(Mono.empty());

            // When
            Mono<Void> result = gatewayFilter.filter(exchange, filterChain);

            // Then
            StepVerifier.create(result).verifyComplete();
            verify(filterChain).filter(exchange);
        }

        @ParameterizedTest(name = "Should allow actuator access from private IP: {0}")
        @CsvSource({
                "10.0.0.5, /actuator/health",
                "192.168.1.100, /actuator/metrics",
                "172.20.0.1, /actuator/info"
        })
        @DisplayName("Should allow actuator access from private IP ranges")
        void apply_ActuatorFromPrivateNetwork_Allows(String ip, String path) {
            // Given
            MockServerHttpRequest request = MockServerHttpRequest
                    .get(path)
                    .header("X-Forwarded-For", ip)
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);

            when(filterChain.filter(exchange)).thenReturn(Mono.empty());

            // When
            Mono<Void> result = gatewayFilter.filter(exchange, filterChain);

            // Then
            StepVerifier.create(result).verifyComplete();
            verify(filterChain).filter(exchange);
        }

        @Test
        @DisplayName("Should deny actuator access from public IP")
        void apply_ActuatorFromPublicIp_Denies() {
            // Given
            MockServerHttpRequest request = MockServerHttpRequest
                    .get("/actuator/health")
                    .header("X-Forwarded-For", "8.8.8.8") // Google DNS - public IP
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);

            // When
            Mono<Void> result = gatewayFilter.filter(exchange, filterChain);

            // Then
            StepVerifier.create(result).verifyComplete();
            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            verify(filterChain, never()).filter(org.mockito.ArgumentMatchers.any());
        }

        @Test
        @DisplayName("Should deny actuator access from 172.15.x.x (outside allowed range)")
        void apply_ActuatorFrom172OutsideRange_Denies() {
            // Given
            MockServerHttpRequest request = MockServerHttpRequest
                    .get("/actuator/health")
                    .header("X-Forwarded-For", "172.15.0.1") // 172.15 is outside 172.16-31
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);

            // When
            Mono<Void> result = gatewayFilter.filter(exchange, filterChain);

            // Then
            StepVerifier.create(result).verifyComplete();
            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }

        @Test
        @DisplayName("Should deny actuator access when no IP can be determined")
        void apply_ActuatorNoIp_Denies() {
            // Given
            MockServerHttpRequest request = MockServerHttpRequest
                    .get("/actuator/health")
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);

            // When
            Mono<Void> result = gatewayFilter.filter(exchange, filterChain);

            // Then
            StepVerifier.create(result).verifyComplete();
            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }
    }

    @Nested
    @DisplayName("X-Forwarded-For Header Handling")
    class XForwardedForTests {

        @Test
        @DisplayName("Should use first IP from X-Forwarded-For chain")
        void apply_XForwardedForChain_UsesFirstIp() {
            // Given - First IP (10.0.0.1) is private, should be allowed
            MockServerHttpRequest request = MockServerHttpRequest
                    .get("/actuator/health")
                    .header("X-Forwarded-For", "10.0.0.1, 8.8.8.8, 192.168.1.1")
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);

            when(filterChain.filter(exchange)).thenReturn(Mono.empty());

            // When
            Mono<Void> result = gatewayFilter.filter(exchange, filterChain);

            // Then
            StepVerifier.create(result).verifyComplete();
            verify(filterChain).filter(exchange);
        }

        @Test
        @DisplayName("Should deny when first IP in chain is public")
        void apply_XForwardedForPublicFirst_Denies() {
            // Given - First IP is public
            MockServerHttpRequest request = MockServerHttpRequest
                    .get("/actuator/health")
                    .header("X-Forwarded-For", "8.8.8.8, 10.0.0.1")
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);

            // When
            Mono<Void> result = gatewayFilter.filter(exchange, filterChain);

            // Then
            StepVerifier.create(result).verifyComplete();
            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }
    }

    @Nested
    @DisplayName("Configuration")
    class ConfigurationTests {

        @Test
        @DisplayName("Should create Config with defaults")
        void config_Defaults_EnabledTrue() {
            // Given
            JwtAuthenticationFilter.Config config = new JwtAuthenticationFilter.Config();

            // Then
            assertThat(config.isEnabled()).isTrue();
        }

        @Test
        @DisplayName("Should allow setting enabled flag")
        void config_SetEnabled_UpdatesValue() {
            // Given
            JwtAuthenticationFilter.Config config = new JwtAuthenticationFilter.Config();

            // When
            config.setEnabled(false);

            // Then
            assertThat(config.isEnabled()).isFalse();
        }
    }

    private JwtValidator.ValidationResult createValidResult(String username, String role) {
        JwtValidator.ValidationResult result = mock(JwtValidator.ValidationResult.class);
        when(result.isValid()).thenReturn(true);
        when(result.getUsername()).thenReturn(username);
        when(result.getRole()).thenReturn(role);
        return result;
    }

    private JwtValidator.ValidationResult createInvalidResult(String errorMessage) {
        JwtValidator.ValidationResult result = mock(JwtValidator.ValidationResult.class);
        when(result.isValid()).thenReturn(false);
        when(result.getErrorMessage()).thenReturn(errorMessage);
        return result;
    }
}
