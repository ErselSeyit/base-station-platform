package com.huawei.auth.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huawei.auth.model.User;
import com.huawei.auth.repository.UserRepository;

import jakarta.servlet.http.Cookie;

/**
 * Integration tests for the complete authentication flow.
 *
 * These tests verify end-to-end functionality including:
 * - User login with valid/invalid credentials
 * - JWT token generation and validation
 * - Cookie-based authentication
 * - Brute-force protection (account lockout)
 * - Logout functionality
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Authentication Flow Integration Tests")
@SuppressWarnings("null")
class AuthenticationFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private static final String LOGIN_URL = "/api/v1/auth/login";
    private static final String LOGOUT_URL = "/api/v1/auth/logout";
    private static final String VALIDATE_URL = "/api/v1/auth/validate";

    private static final String TEST_USERNAME = "testuser";
    private static final String TEST_PASSWORD = "securePassword123";
    private static final String TEST_ROLE = "USER";

    @BeforeEach
    void setUp() {
        // Clean up and create test user
        userRepository.deleteAll();

        User testUser = new User();
        testUser.setUsername(TEST_USERNAME);
        testUser.setPasswordHash(passwordEncoder.encode(TEST_PASSWORD));
        testUser.setRole(TEST_ROLE);
        testUser.setEnabled(true);
        userRepository.save(testUser);
    }

    @Nested
    @DisplayName("Login Flow")
    class LoginFlowTests {

        @Test
        @DisplayName("Should successfully login with valid credentials and receive JWT token")
        void login_ValidCredentials_ReturnsTokenAndSetsCookie() throws Exception {
            // Given
            String requestBody = """
                    {"username": "%s", "password": "%s"}
                    """.formatted(TEST_USERNAME, TEST_PASSWORD);

            // When/Then
            MvcResult result = mockMvc.perform(post(LOGIN_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.token").isNotEmpty())
                    .andExpect(jsonPath("$.username").value(TEST_USERNAME))
                    .andExpect(jsonPath("$.role").value(TEST_ROLE))
                    .andExpect(cookie().exists("auth_token"))
                    .andExpect(cookie().httpOnly("auth_token", true))
                    .andReturn();

            // Verify token structure (JWT format: header.payload.signature)
            JsonNode responseJson = objectMapper.readTree(result.getResponse().getContentAsString());
            String token = responseJson.get("token").asText();
            assertThat(token.split("\\.")).hasSize(3);
        }

        @Test
        @DisplayName("Should return 401 for invalid password")
        void login_InvalidPassword_Returns401() throws Exception {
            // Given
            String requestBody = """
                    {"username": "%s", "password": "wrongPassword123"}
                    """.formatted(TEST_USERNAME);

            // When/Then
            mockMvc.perform(post(LOGIN_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error").value("Invalid credentials"))
                    .andExpect(jsonPath("$.remainingAttempts").isNumber());
        }

        @Test
        @DisplayName("Should return 401 for non-existent user")
        void login_NonExistentUser_Returns401() throws Exception {
            // Given
            String requestBody = """
                    {"username": "nonexistent", "password": "anyPassword123"}
                    """;

            // When/Then
            mockMvc.perform(post(LOGIN_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error").value("Invalid credentials"));
        }

        @Test
        @DisplayName("Should return 400 for missing username")
        void login_MissingUsername_Returns400() throws Exception {
            // Given
            String requestBody = """
                    {"password": "securePassword123"}
                    """;

            // When/Then
            mockMvc.perform(post(LOGIN_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Should return 400 for password too short")
        void login_PasswordTooShort_Returns400() throws Exception {
            // Given - password less than 12 characters
            String requestBody = """
                    {"username": "testuser", "password": "short"}
                    """;

            // When/Then
            mockMvc.perform(post(LOGIN_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Should not authenticate disabled user")
        void login_DisabledUser_Returns401() throws Exception {
            // Given - disable the user
            User user = userRepository.findByUsername(TEST_USERNAME).orElseThrow();
            user.setEnabled(false);
            userRepository.save(user);

            String requestBody = """
                    {"username": "%s", "password": "%s"}
                    """.formatted(TEST_USERNAME, TEST_PASSWORD);

            // When/Then
            mockMvc.perform(post(LOGIN_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("Token Validation Flow")
    class TokenValidationTests {

        @Test
        @DisplayName("Should validate a freshly issued token")
        void validate_FreshToken_Returns200() throws Exception {
            // Given - login to get a token
            String loginRequest = """
                    {"username": "%s", "password": "%s"}
                    """.formatted(TEST_USERNAME, TEST_PASSWORD);

            MvcResult loginResult = mockMvc.perform(post(LOGIN_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(loginRequest))
                    .andExpect(status().isOk())
                    .andReturn();

            JsonNode responseJson = objectMapper.readTree(loginResult.getResponse().getContentAsString());
            String token = responseJson.get("token").asText();

            // When/Then - validate the token
            mockMvc.perform(get(VALIDATE_URL)
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Should reject invalid token")
        void validate_InvalidToken_Returns401() throws Exception {
            // Given
            String invalidToken = "eyJhbGciOiJIUzI1NiJ9.invalid.signature";

            // When/Then
            mockMvc.perform(get(VALIDATE_URL)
                            .header("Authorization", "Bearer " + invalidToken))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Should reject token without Bearer prefix")
        void validate_NoBearerPrefix_Returns401() throws Exception {
            // Given - login to get a token
            String loginRequest = """
                    {"username": "%s", "password": "%s"}
                    """.formatted(TEST_USERNAME, TEST_PASSWORD);

            MvcResult loginResult = mockMvc.perform(post(LOGIN_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(loginRequest))
                    .andExpect(status().isOk())
                    .andReturn();

            JsonNode responseJson = objectMapper.readTree(loginResult.getResponse().getContentAsString());
            String token = responseJson.get("token").asText();

            // When/Then - validate without Bearer prefix
            mockMvc.perform(get(VALIDATE_URL)
                            .header("Authorization", token))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("Logout Flow")
    class LogoutTests {

        @Test
        @DisplayName("Should clear auth cookie on logout")
        void logout_WithToken_ClearsCookie() throws Exception {
            // Given - login first
            String loginRequest = """
                    {"username": "%s", "password": "%s"}
                    """.formatted(TEST_USERNAME, TEST_PASSWORD);

            MvcResult loginResult = mockMvc.perform(post(LOGIN_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(loginRequest))
                    .andExpect(status().isOk())
                    .andReturn();

            JsonNode responseJson = objectMapper.readTree(loginResult.getResponse().getContentAsString());
            String token = responseJson.get("token").asText();

            // When/Then - logout
            mockMvc.perform(post(LOGOUT_URL)
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(cookie().maxAge("auth_token", 0));
        }

        @Test
        @DisplayName("Should succeed even without token")
        void logout_NoToken_Succeeds() throws Exception {
            // When/Then
            mockMvc.perform(post(LOGOUT_URL))
                    .andExpect(status().isOk())
                    .andExpect(cookie().maxAge("auth_token", 0));
        }
    }

    @Nested
    @DisplayName("Brute Force Protection")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class BruteForceProtectionTests {

        private static final String LOCKOUT_TEST_USER = "lockouttest";
        private static final String LOCKOUT_TEST_PASSWORD = "lockoutPassword123";

        @BeforeEach
        void setUpLockoutUser() {
            // Create a separate user for lockout tests
            if (userRepository.findByUsername(LOCKOUT_TEST_USER).isEmpty()) {
                User lockoutUser = new User();
                lockoutUser.setUsername(LOCKOUT_TEST_USER);
                lockoutUser.setPasswordHash(passwordEncoder.encode(LOCKOUT_TEST_PASSWORD));
                lockoutUser.setRole("USER");
                lockoutUser.setEnabled(true);
                userRepository.save(lockoutUser);
            }
        }

        @Test
        @Order(1)
        @DisplayName("Should decrement remaining attempts on each failed login")
        void login_FailedAttempts_DecrementsCounter() throws Exception {
            // Given
            String uniqueUser = "decrement_test_user";
            User user = new User();
            user.setUsername(uniqueUser);
            user.setPasswordHash(passwordEncoder.encode("validPassword123"));
            user.setRole("USER");
            user.setEnabled(true);
            userRepository.save(user);

            String requestBody = """
                    {"username": "%s", "password": "wrongPassword123"}
                    """.formatted(uniqueUser);

            // When - first failed attempt
            MvcResult result1 = mockMvc.perform(post(LOGIN_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isUnauthorized())
                    .andReturn();

            JsonNode response1 = objectMapper.readTree(result1.getResponse().getContentAsString());
            int remaining1 = response1.get("remainingAttempts").asInt();

            // When - second failed attempt
            MvcResult result2 = mockMvc.perform(post(LOGIN_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isUnauthorized())
                    .andReturn();

            JsonNode response2 = objectMapper.readTree(result2.getResponse().getContentAsString());
            int remaining2 = response2.get("remainingAttempts").asInt();

            // Then
            assertThat(remaining2).isLessThan(remaining1);
        }

        @Test
        @Order(2)
        @DisplayName("Should lock account after max failed attempts")
        void login_MaxFailedAttempts_LocksAccount() throws Exception {
            // Given - use unique user for this test
            String uniqueUser = "lockout_user_" + System.currentTimeMillis();
            User user = new User();
            user.setUsername(uniqueUser);
            user.setPasswordHash(passwordEncoder.encode("validPassword123"));
            user.setRole("USER");
            user.setEnabled(true);
            userRepository.save(user);

            String wrongPasswordRequest = """
                    {"username": "%s", "password": "wrongPassword123"}
                    """.formatted(uniqueUser);

            // When - exhaust all attempts (5 is the default max)
            for (int i = 0; i < 5; i++) {
                mockMvc.perform(post(LOGIN_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(wrongPasswordRequest))
                        .andExpect(status().isUnauthorized());
            }

            // Then - next attempt should be rate limited
            mockMvc.perform(post(LOGIN_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(wrongPasswordRequest))
                    .andExpect(status().isTooManyRequests())
                    .andExpect(jsonPath("$.retryAfterSeconds").isNumber());
        }

        @Test
        @Order(3)
        @DisplayName("Should prevent login even with correct password when locked")
        void login_LockedAccount_RejectsValidCredentials() throws Exception {
            // Given - use unique user for this test
            String uniqueUser = "locked_valid_user_" + System.currentTimeMillis();
            String validPassword = "validPassword123";

            User user = new User();
            user.setUsername(uniqueUser);
            user.setPasswordHash(passwordEncoder.encode(validPassword));
            user.setRole("USER");
            user.setEnabled(true);
            userRepository.save(user);

            String wrongPasswordRequest = """
                    {"username": "%s", "password": "wrongPassword123"}
                    """.formatted(uniqueUser);

            // Lock the account
            for (int i = 0; i < 5; i++) {
                mockMvc.perform(post(LOGIN_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(wrongPasswordRequest))
                        .andExpect(status().isUnauthorized());
            }

            // When - try with correct password while locked
            String correctPasswordRequest = """
                    {"username": "%s", "password": "%s"}
                    """.formatted(uniqueUser, validPassword);

            // Then - should still be rejected due to lockout
            mockMvc.perform(post(LOGIN_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(correctPasswordRequest))
                    .andExpect(status().isTooManyRequests());
        }
    }

    @Nested
    @DisplayName("Complete Authentication Lifecycle")
    class FullLifecycleTests {

        @Test
        @DisplayName("Should complete full login -> use token -> logout cycle")
        void fullAuthenticationLifecycle() throws Exception {
            // Step 1: Login
            String loginRequest = """
                    {"username": "%s", "password": "%s"}
                    """.formatted(TEST_USERNAME, TEST_PASSWORD);

            MvcResult loginResult = mockMvc.perform(post(LOGIN_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(loginRequest))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.token").isNotEmpty())
                    .andReturn();

            JsonNode loginResponse = objectMapper.readTree(loginResult.getResponse().getContentAsString());
            String token = loginResponse.get("token").asText();
            Cookie authCookie = loginResult.getResponse().getCookie("auth_token");

            assertThat(token).isNotBlank();
            assertThat(authCookie)
                    .isNotNull()
                    .extracting(Cookie::getValue)
                    .isEqualTo(token);

            // Step 2: Validate token
            mockMvc.perform(get(VALIDATE_URL)
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk());

            // Step 3: Logout
            mockMvc.perform(post(LOGOUT_URL)
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(cookie().maxAge("auth_token", 0));

            // Step 4: Token is still technically valid (server doesn't revoke)
            // but cookie has been cleared - this is expected behavior
            // Real token revocation would require a blocklist
            mockMvc.perform(get(VALIDATE_URL)
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk()); // Token still valid until expiry
        }

        @Test
        @DisplayName("Should allow re-login after logout")
        void reLoginAfterLogout() throws Exception {
            // Given - login
            String loginRequest = """
                    {"username": "%s", "password": "%s"}
                    """.formatted(TEST_USERNAME, TEST_PASSWORD);

            MvcResult firstLogin = mockMvc.perform(post(LOGIN_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(loginRequest))
                    .andExpect(status().isOk())
                    .andReturn();

            JsonNode firstResponse = objectMapper.readTree(firstLogin.getResponse().getContentAsString());
            String firstToken = firstResponse.get("token").asText();

            // When - logout
            mockMvc.perform(post(LOGOUT_URL)
                            .header("Authorization", "Bearer " + firstToken))
                    .andExpect(status().isOk());

            // Then - re-login should work and return a new token
            MvcResult secondLogin = mockMvc.perform(post(LOGIN_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(loginRequest))
                    .andExpect(status().isOk())
                    .andReturn();

            JsonNode secondResponse = objectMapper.readTree(secondLogin.getResponse().getContentAsString());
            String secondToken = secondResponse.get("token").asText();

            assertThat(secondToken).isNotBlank();
            // Tokens might be different due to different issued-at times
            // but both should be valid
            mockMvc.perform(get(VALIDATE_URL)
                            .header("Authorization", "Bearer " + secondToken))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("Multiple User Scenarios")
    class MultipleUserTests {

        @Test
        @DisplayName("Should authenticate different users independently")
        void login_MultipleUsers_IndependentSessions() throws Exception {
            // Given - create second user
            User adminUser = new User();
            adminUser.setUsername("adminuser");
            adminUser.setPasswordHash(passwordEncoder.encode("adminPassword123"));
            adminUser.setRole("ADMIN");
            adminUser.setEnabled(true);
            userRepository.save(adminUser);

            // When - login as regular user
            String userLoginRequest = """
                    {"username": "%s", "password": "%s"}
                    """.formatted(TEST_USERNAME, TEST_PASSWORD);

            MvcResult userResult = mockMvc.perform(post(LOGIN_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(userLoginRequest))
                    .andExpect(status().isOk())
                    .andReturn();

            // And - login as admin
            String adminLoginRequest = """
                    {"username": "adminuser", "password": "adminPassword123"}
                    """;

            MvcResult adminResult = mockMvc.perform(post(LOGIN_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(adminLoginRequest))
                    .andExpect(status().isOk())
                    .andReturn();

            // Then - both tokens should be valid and different
            JsonNode userResponse = objectMapper.readTree(userResult.getResponse().getContentAsString());
            JsonNode adminResponse = objectMapper.readTree(adminResult.getResponse().getContentAsString());

            String userToken = userResponse.get("token").asText();
            String adminToken = adminResponse.get("token").asText();

            assertThat(userToken).isNotEqualTo(adminToken);
            assertThat(userResponse.get("role").asText()).isEqualTo("USER");
            assertThat(adminResponse.get("role").asText()).isEqualTo("ADMIN");

            // Both tokens should validate
            mockMvc.perform(get(VALIDATE_URL)
                            .header("Authorization", "Bearer " + userToken))
                    .andExpect(status().isOk());

            mockMvc.perform(get(VALIDATE_URL)
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk());
        }
    }
}
