package com.huawei.auth.controller;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huawei.auth.dto.LoginRequest;
import com.huawei.auth.model.User;
import com.huawei.auth.service.LoginAttemptService;
import com.huawei.auth.service.RefreshTokenService;
import com.huawei.auth.service.SecurityAuditService;
import com.huawei.auth.service.UserService;
import com.huawei.auth.util.JwtUtil;

/**
 * Controller tests for AuthController.
 *
 * Tests cover login, logout, and token validation endpoints
 * including security features like brute-force protection.
 */
@WebMvcTest(AuthController.class)
@Import(TestSecurityConfig.class)
@ActiveProfiles("test")
@DisplayName("AuthController Tests")
@SuppressWarnings("null")
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private JwtUtil jwtUtil;

    @MockitoBean
    private LoginAttemptService loginAttemptService;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private SecurityAuditService auditService;

    @MockitoBean
    private RefreshTokenService refreshTokenService;

    private static final String LOGIN_URL = "/api/v1/auth/login";
    private static final String LOGOUT_URL = "/api/v1/auth/logout";
    private static final String VALIDATE_URL = "/api/v1/auth/validate";

    @Nested
    @DisplayName("POST /api/v1/auth/login")
    class LoginTests {

        @Test
        @DisplayName("Should return token for valid credentials")
        void login_ValidCredentials_ReturnsToken() throws Exception {
            // Given
            String username = "testuser";
            String password = "securePassword123";
            String token = "eyJhbGciOiJIUzI1NiJ9.test.signature";
            String role = "USER";

            User user = createUser(username, role);
            LoginRequest request = new LoginRequest(username, password);

            when(loginAttemptService.isBlocked(anyString())).thenReturn(false);
            when(userService.authenticate(username, password)).thenReturn(Optional.of(user));
            when(jwtUtil.generateToken(username, role)).thenReturn(token);

            // When/Then
            mockMvc.perform(post(LOGIN_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.token").value(token))
                    .andExpect(jsonPath("$.username").value(username))
                    .andExpect(jsonPath("$.role").value(role))
                    .andExpect(cookie().exists("auth_token"))
                    .andExpect(cookie().httpOnly("auth_token", true));

            verify(loginAttemptService).recordSuccessfulLogin(anyString());
            verify(auditService).logLoginSuccess(eq(username), anyString());
        }

        @Test
        @DisplayName("Should return 401 for invalid credentials")
        void login_InvalidCredentials_Returns401() throws Exception {
            // Given
            String username = "testuser";
            String password = "wrongPassword1";
            LoginRequest request = new LoginRequest(username, password);

            when(loginAttemptService.isBlocked(anyString())).thenReturn(false);
            when(userService.authenticate(username, password)).thenReturn(Optional.empty());
            when(loginAttemptService.getRemainingAttempts(anyString())).thenReturn(4);

            // When/Then
            mockMvc.perform(post(LOGIN_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error").value("Invalid credentials"))
                    .andExpect(jsonPath("$.remainingAttempts").value(4));

            verify(loginAttemptService).recordFailedAttempt(anyString());
            verify(auditService).logLoginFailure(eq(username), anyString(), anyString(), eq(4));
        }

        @Test
        @DisplayName("Should return 429 when account is locked")
        void login_AccountLocked_Returns429() throws Exception {
            // Given
            String username = "lockeduser";
            String password = "anyPassword12";
            LoginRequest request = new LoginRequest(username, password);

            when(loginAttemptService.isBlocked(anyString())).thenReturn(true);
            when(loginAttemptService.getRemainingLockoutSeconds(anyString())).thenReturn(300L);

            // When/Then
            mockMvc.perform(post(LOGIN_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isTooManyRequests())
                    .andExpect(jsonPath("$.error").exists())
                    .andExpect(jsonPath("$.retryAfterSeconds").value(300));

            verify(userService, never()).authenticate(anyString(), anyString());
            verify(auditService).logAccountLocked(eq(username), anyString(), eq(300L));
        }

        @Test
        @DisplayName("Should return 400 for missing username")
        void login_MissingUsername_Returns400() throws Exception {
            // Given
            String json = "{\"password\": \"securePassword123\"}";

            // When/Then
            mockMvc.perform(post(LOGIN_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Should return 400 for missing password")
        void login_MissingPassword_Returns400() throws Exception {
            // Given
            String json = "{\"username\": \"testuser\"}";

            // When/Then
            mockMvc.perform(post(LOGIN_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Should return 400 for password too short")
        void login_PasswordTooShort_Returns400() throws Exception {
            // Given - password less than 12 chars
            LoginRequest request = new LoginRequest("testuser", "short");

            // When/Then
            mockMvc.perform(post(LOGIN_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Should return 400 for username too short")
        void login_UsernameTooShort_Returns400() throws Exception {
            // Given - username less than 3 chars
            LoginRequest request = new LoginRequest("ab", "securePassword123");

            // When/Then
            mockMvc.perform(post(LOGIN_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Should return token for admin user")
        void login_AdminUser_ReturnsAdminToken() throws Exception {
            // Given
            String username = "admin";
            String password = "adminPassword123";
            String token = "admin.token.signature";
            String role = "ADMIN";

            User user = createUser(username, role);
            LoginRequest request = new LoginRequest(username, password);

            when(loginAttemptService.isBlocked(anyString())).thenReturn(false);
            when(userService.authenticate(username, password)).thenReturn(Optional.of(user));
            when(jwtUtil.generateToken(username, role)).thenReturn(token);

            // When/Then
            mockMvc.perform(post(LOGIN_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.role").value("ADMIN"));
        }

        @Test
        @DisplayName("Should use X-Forwarded-For header for client IP")
        void login_WithXForwardedFor_UsesForwardedIp() throws Exception {
            // Given
            String username = "testuser";
            String password = "securePassword123";
            String forwardedIp = "10.0.0.1";

            User user = createUser(username, "USER");
            LoginRequest request = new LoginRequest(username, password);

            when(loginAttemptService.isBlocked(anyString())).thenReturn(false);
            when(userService.authenticate(username, password)).thenReturn(Optional.of(user));
            when(jwtUtil.generateToken(anyString(), anyString())).thenReturn("token");

            // When/Then
            mockMvc.perform(post(LOGIN_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("X-Forwarded-For", forwardedIp + ", 192.168.1.1")
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk());

            // Verify the lock key includes the forwarded IP
            verify(loginAttemptService).isBlocked(username + ":" + forwardedIp);
        }
    }

    @Nested
    @DisplayName("POST /api/v1/auth/logout")
    class LogoutTests {

        @Test
        @DisplayName("Should clear auth cookie on logout")
        void logout_ValidRequest_ClearsCookie() throws Exception {
            // When/Then
            mockMvc.perform(post(LOGOUT_URL))
                    .andExpect(status().isOk())
                    .andExpect(cookie().maxAge("auth_token", 0));
        }

        @Test
        @DisplayName("Should log username when token provided")
        void logout_WithToken_LogsUsername() throws Exception {
            // Given
            String token = "valid.jwt.token";
            String username = "testuser";

            when(jwtUtil.extractUsername(token)).thenReturn(username);

            // When/Then
            mockMvc.perform(post(LOGOUT_URL)
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk());

            verify(jwtUtil).extractUsername(token);
            verify(auditService).logLogout(eq(username), anyString());
        }

        @Test
        @DisplayName("Should succeed even without token")
        void logout_NoToken_Succeeds() throws Exception {
            // When/Then
            mockMvc.perform(post(LOGOUT_URL))
                    .andExpect(status().isOk());

            verify(jwtUtil, never()).extractUsername(anyString());
        }

        @Test
        @DisplayName("Should handle invalid token gracefully")
        void logout_InvalidToken_SucceedsAnyway() throws Exception {
            // Given
            String invalidToken = "invalid.token";

            when(jwtUtil.extractUsername(invalidToken)).thenThrow(new RuntimeException("Invalid token"));

            // When/Then
            mockMvc.perform(post(LOGOUT_URL)
                            .header("Authorization", "Bearer " + invalidToken))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/auth/validate")
    class ValidateTests {

        @Test
        @DisplayName("Should return 200 for valid token")
        void validate_ValidToken_Returns200() throws Exception {
            // Given
            String token = "valid.jwt.token";
            String username = "testuser";

            when(jwtUtil.extractUsername(token)).thenReturn(username);
            when(jwtUtil.validateToken(token, username)).thenReturn(true);

            // When/Then
            mockMvc.perform(get(VALIDATE_URL)
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Should return 401 for invalid token")
        void validate_InvalidToken_Returns401() throws Exception {
            // Given
            String token = "invalid.jwt.token";
            String username = "testuser";

            when(jwtUtil.extractUsername(token)).thenReturn(username);
            when(jwtUtil.validateToken(token, username)).thenReturn(false);

            // When/Then
            mockMvc.perform(get(VALIDATE_URL)
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Should return 401 for expired token")
        void validate_ExpiredToken_Returns401() throws Exception {
            // Given
            String token = "expired.jwt.token";

            when(jwtUtil.extractUsername(token)).thenThrow(new io.jsonwebtoken.ExpiredJwtException(null, null, "Expired"));

            // When/Then
            mockMvc.perform(get(VALIDATE_URL)
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Should return 401 for malformed token")
        void validate_MalformedToken_Returns401() throws Exception {
            // Given
            String token = "malformed";

            when(jwtUtil.extractUsername(token)).thenThrow(new io.jsonwebtoken.MalformedJwtException("Malformed"));

            // When/Then
            mockMvc.perform(get(VALIDATE_URL)
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Should return error when Authorization header missing")
        void validate_NoAuthHeader_ReturnsError() throws Exception {
            // When/Then - Missing required header returns 400 Bad Request
            mockMvc.perform(get(VALIDATE_URL))
                    .andExpect(status().is4xxClientError());
        }

        @Test
        @DisplayName("Should return 401 when Bearer prefix missing")
        void validate_NoBearerPrefix_Returns401() throws Exception {
            // When/Then
            mockMvc.perform(get(VALIDATE_URL)
                            .header("Authorization", "token.without.bearer"))
                    .andExpect(status().isUnauthorized());
        }
    }

    private User createUser(String username, String role) {
        User user = new User();
        user.setId(1L);
        user.setUsername(username);
        user.setPasswordHash("$2a$10$encoded");
        user.setRole(role);
        user.setEnabled(true);
        return user;
    }
}
