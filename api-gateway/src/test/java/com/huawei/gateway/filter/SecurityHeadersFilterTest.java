package com.huawei.gateway.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

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
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.util.ReflectionTestUtils;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Unit tests for SecurityHeadersFilter.
 *
 * Tests verify that security headers are correctly added to responses
 * for protection against common web attacks.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SecurityHeadersFilter Tests")
@SuppressWarnings("null")
class SecurityHeadersFilterTest {

    @Mock
    private GatewayFilterChain filterChain;

    private SecurityHeadersFilter filter;

    @BeforeEach
    void setUp() {
        filter = new SecurityHeadersFilter();
        // Set default values
        ReflectionTestUtils.setField(filter, "hstsEnabled", true);
        ReflectionTestUtils.setField(filter, "hstsMaxAge", 31536000L);
        ReflectionTestUtils.setField(filter, "cspEnabled", true);
        ReflectionTestUtils.setField(filter, "frameOptions", "DENY");
    }

    /**
     * Helper to run filter and trigger beforeCommit callbacks.
     * The filter uses beforeCommit to add headers, so we must complete the response.
     */
    private HttpHeaders runFilterAndGetHeaders(MockServerWebExchange exchange) {
        when(filterChain.filter(exchange)).thenReturn(Mono.empty());

        // Run filter (registers beforeCommit callback)
        StepVerifier.create(filter.filter(exchange, filterChain)).verifyComplete();

        // Trigger beforeCommit callbacks by completing the response
        StepVerifier.create(exchange.getResponse().setComplete()).verifyComplete();

        return exchange.getResponse().getHeaders();
    }

    @Nested
    @DisplayName("Standard Security Headers")
    class StandardHeadersTests {

        @ParameterizedTest(name = "Should add {0} header with value {1}")
        @CsvSource({
                "X-Content-Type-Options, nosniff",
                "X-Frame-Options, DENY",
                "X-XSS-Protection, 1; mode=block",
                "Referrer-Policy, strict-origin-when-cross-origin"
        })
        @DisplayName("Should add standard security headers")
        void filter_AnyRequest_AddsSecurityHeader(String headerName, String expectedValue) {
            // Given
            MockServerHttpRequest request = MockServerHttpRequest.get("/api/test").build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);

            // When
            HttpHeaders headers = runFilterAndGetHeaders(exchange);

            // Then
            assertThat(headers.getFirst(headerName)).isEqualTo(expectedValue);
        }

        @Test
        @DisplayName("Should add Permissions-Policy header")
        void filter_AnyRequest_AddsPermissionsPolicy() {
            // Given
            MockServerHttpRequest request = MockServerHttpRequest.get("/api/test").build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);

            // When
            HttpHeaders headers = runFilterAndGetHeaders(exchange);

            // Then
            String permissionsPolicy = headers.getFirst("Permissions-Policy");
            assertThat(permissionsPolicy)
                    .contains("camera=()")
                    .contains("microphone=()")
                    .contains("geolocation=()");
        }
    }

    @Nested
    @DisplayName("HSTS Header")
    class HstsHeaderTests {

        @Test
        @DisplayName("Should add HSTS header when enabled")
        void filter_HstsEnabled_AddsHstsHeader() {
            // Given
            ReflectionTestUtils.setField(filter, "hstsEnabled", true);
            ReflectionTestUtils.setField(filter, "hstsMaxAge", 31536000L);

            MockServerHttpRequest request = MockServerHttpRequest.get("/api/test").build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);

            // When
            HttpHeaders headers = runFilterAndGetHeaders(exchange);

            // Then
            String hsts = headers.getFirst("Strict-Transport-Security");
            assertThat(hsts)
                    .contains("max-age=31536000")
                    .contains("includeSubDomains")
                    .contains("preload");
        }

        @Test
        @DisplayName("Should not add HSTS header when disabled")
        void filter_HstsDisabled_NoHstsHeader() {
            // Given
            ReflectionTestUtils.setField(filter, "hstsEnabled", false);

            MockServerHttpRequest request = MockServerHttpRequest.get("/api/test").build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);

            // When
            HttpHeaders headers = runFilterAndGetHeaders(exchange);

            // Then
            assertThat(headers.getFirst("Strict-Transport-Security")).isNull();
        }

        @Test
        @DisplayName("Should use custom max-age value")
        void filter_CustomMaxAge_UsesCustomValue() {
            // Given
            ReflectionTestUtils.setField(filter, "hstsEnabled", true);
            ReflectionTestUtils.setField(filter, "hstsMaxAge", 86400L); // 1 day

            MockServerHttpRequest request = MockServerHttpRequest.get("/api/test").build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);

            // When
            HttpHeaders headers = runFilterAndGetHeaders(exchange);

            // Then
            assertThat(headers.getFirst("Strict-Transport-Security")).contains("max-age=86400");
        }
    }

    @Nested
    @DisplayName("CSP Header")
    class CspHeaderTests {

        @Test
        @DisplayName("Should add CSP header when enabled")
        void filter_CspEnabled_AddsCspHeader() {
            // Given
            ReflectionTestUtils.setField(filter, "cspEnabled", true);

            MockServerHttpRequest request = MockServerHttpRequest.get("/api/test").build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);

            // When
            HttpHeaders headers = runFilterAndGetHeaders(exchange);

            // Then
            String csp = headers.getFirst("Content-Security-Policy");
            assertThat(csp)
                    .contains("default-src 'self'")
                    .contains("frame-ancestors 'none'")
                    .contains("script-src")
                    .contains("style-src");
        }

        @Test
        @DisplayName("Should not add CSP header when disabled")
        void filter_CspDisabled_NoCspHeader() {
            // Given
            ReflectionTestUtils.setField(filter, "cspEnabled", false);

            MockServerHttpRequest request = MockServerHttpRequest.get("/api/test").build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);

            // When
            HttpHeaders headers = runFilterAndGetHeaders(exchange);

            // Then
            assertThat(headers.getFirst("Content-Security-Policy")).isNull();
        }
    }

    @Nested
    @DisplayName("Cache Control for Sensitive Paths")
    class CacheControlTests {

        @ParameterizedTest(name = "Should add no-cache headers for: {0}")
        @ValueSource(strings = {
                "/api/v1/auth/login",
                "/api/v1/auth/logout",
                "/api/v1/user/profile",
                "/api/v1/admin/settings"
        })
        @DisplayName("Should add cache control headers for sensitive paths")
        void filter_SensitivePath_AddsCacheControl(String path) {
            // Given
            MockServerHttpRequest request = MockServerHttpRequest.get(path).build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);

            // When
            HttpHeaders headers = runFilterAndGetHeaders(exchange);

            // Then
            assertThat(headers.getFirst("Cache-Control")).contains("no-store");
            assertThat(headers.getFirst("Pragma")).isEqualTo("no-cache");
            assertThat(headers.getFirst("Expires")).isEqualTo("0");
        }

        @ParameterizedTest(name = "Should not add no-cache headers for: {0}")
        @ValueSource(strings = {
                "/api/v1/stations",
                "/api/v1/metrics",
                "/api/v1/alerts"
        })
        @DisplayName("Should not add cache control headers for non-sensitive paths")
        void filter_NonSensitivePath_NoCacheControl(String path) {
            // Given
            MockServerHttpRequest request = MockServerHttpRequest.get(path).build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);

            // When
            HttpHeaders headers = runFilterAndGetHeaders(exchange);

            // Then
            assertThat(headers.getFirst("Cache-Control")).isNull();
            assertThat(headers.getFirst("Pragma")).isNull();
        }
    }

    @Nested
    @DisplayName("Custom Frame Options")
    class FrameOptionsTests {

        @Test
        @DisplayName("Should use custom frame options value")
        void filter_CustomFrameOptions_UsesCustomValue() {
            // Given
            ReflectionTestUtils.setField(filter, "frameOptions", "SAMEORIGIN");

            MockServerHttpRequest request = MockServerHttpRequest.get("/api/test").build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);

            // When
            HttpHeaders headers = runFilterAndGetHeaders(exchange);

            // Then
            assertThat(headers.getFirst("X-Frame-Options")).isEqualTo("SAMEORIGIN");
        }
    }

    @Nested
    @DisplayName("Filter Order")
    class FilterOrderTests {

        @Test
        @DisplayName("Should have lowest precedence minus 1")
        void getOrder_ReturnsLowestPrecedenceMinusOne() {
            // When
            int order = filter.getOrder();

            // Then
            assertThat(order).isEqualTo(Ordered.LOWEST_PRECEDENCE - 1);
        }
    }
}
