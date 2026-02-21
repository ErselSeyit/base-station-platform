package com.huawei.common.security;

import com.huawei.common.constants.HttpHeaders;
import com.huawei.common.constants.SecurityConstants;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;

class InternalAuthFilterTest {

    private static final String TEST_SECRET = "test-secret-key-for-hmac-verification";

    private InternalAuthFilter filter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private MockFilterChain chain;

    @BeforeEach
    void setUp() {
        filter = new InternalAuthFilter();
        ReflectionTestUtils.setField(filter, "internalSecret", TEST_SECRET);
        ReflectionTestUtils.setField(filter, "authEnabled", true);

        request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/stations");
        response = new MockHttpServletResponse();
        chain = new MockFilterChain();
    }

    @Test
    void validHmacShouldProceed() throws ServletException, IOException {
        String payload = buildPayload("admin", "ADMIN", System.currentTimeMillis());
        String signature = computeHmac(payload, TEST_SECRET);
        request.addHeader(HttpHeaders.HEADER_INTERNAL_AUTH, signature + "." + payload);

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void invalidSignatureShouldReturn403() throws ServletException, IOException {
        String payload = buildPayload("admin", "ADMIN", System.currentTimeMillis());
        request.addHeader(HttpHeaders.HEADER_INTERNAL_AUTH, "invalidsignature." + payload);

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void missingAuthHeaderShouldReturn403() throws ServletException, IOException {
        filter.doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void malformedHeaderWithoutDotSeparatorShouldReturn403() throws ServletException, IOException {
        request.addHeader(HttpHeaders.HEADER_INTERNAL_AUTH, "no-dot-separator-here");

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void expiredTimestampShouldReturn403() throws ServletException, IOException {
        long expiredTimestamp = System.currentTimeMillis() - SecurityConstants.MAX_TIMESTAMP_AGE_MS - 5000;
        String payload = buildPayload("admin", "ADMIN", expiredTimestamp);
        String signature = computeHmac(payload, TEST_SECRET);
        request.addHeader(HttpHeaders.HEADER_INTERNAL_AUTH, signature + "." + payload);

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void futureTimestampShouldReturn403() throws ServletException, IOException {
        long futureTimestamp = System.currentTimeMillis() + 60_000;
        String payload = buildPayload("admin", "ADMIN", futureTimestamp);
        String signature = computeHmac(payload, TEST_SECRET);
        request.addHeader(HttpHeaders.HEADER_INTERNAL_AUTH, signature + "." + payload);

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void actuatorHealthEndpointBypassesAuth() throws ServletException, IOException {
        request.setRequestURI("/actuator/health");

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void authLoginEndpointBypassesAuth() throws ServletException, IOException {
        request.setRequestURI("/api/v1/auth/login");

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void authRegisterEndpointBypassesAuth() throws ServletException, IOException {
        request.setRequestURI("/api/v1/auth/register");

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void authValidateEndpointBypassesAuth() throws ServletException, IOException {
        request.setRequestURI("/api/v1/auth/validate");

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void authDisabledBypassesVerification() throws ServletException, IOException {
        ReflectionTestUtils.setField(filter, "authEnabled", false);

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void blankSecretReturns500() throws ServletException, IOException {
        ReflectionTestUtils.setField(filter, "internalSecret", "");
        String payload = buildPayload("admin", "ADMIN", System.currentTimeMillis());
        request.addHeader(HttpHeaders.HEADER_INTERNAL_AUTH, "somesig." + payload);

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(500);
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void nullSecretReturns500() throws ServletException, IOException {
        ReflectionTestUtils.setField(filter, "internalSecret", null);
        String payload = buildPayload("admin", "ADMIN", System.currentTimeMillis());
        request.addHeader(HttpHeaders.HEADER_INTERNAL_AUTH, "somesig." + payload);

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(500);
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void blankAuthHeaderShouldReturn403() throws ServletException, IOException {
        request.addHeader(HttpHeaders.HEADER_INTERNAL_AUTH, "   ");

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void invalidPayloadFormatShouldReturn403() throws ServletException, IOException {
        String payload = "onlytwocolons:here";
        String signature = computeHmac(payload, TEST_SECRET);
        request.addHeader(HttpHeaders.HEADER_INTERNAL_AUTH, signature + "." + payload);

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void nonNumericTimestampShouldReturn403() throws ServletException, IOException {
        String payload = "admin:ADMIN:not-a-number";
        String signature = computeHmac(payload, TEST_SECRET);
        request.addHeader(HttpHeaders.HEADER_INTERNAL_AUTH, signature + "." + payload);

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(chain.getRequest()).isNull();
    }

    private static String buildPayload(String username, String role, long timestamp) {
        return username + ":" + role + ":" + timestamp;
    }

    private static String computeHmac(String data, String secret) {
        try {
            Mac hmac = Mac.getInstance(SecurityConstants.HMAC_ALGORITHM);
            SecretKeySpec secretKey = new SecretKeySpec(
                    secret.getBytes(StandardCharsets.UTF_8),
                    SecurityConstants.HMAC_ALGORITHM
            );
            hmac.init(secretKey);
            byte[] hash = hmac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new RuntimeException(e);
        }
    }
}
