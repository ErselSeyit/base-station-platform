package com.huawei.auth.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for User domain behavior methods.
 */
@DisplayName("User Domain Behavior Tests")
class UserDomainTest {

    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setUsername("testuser");
        user.setPasswordHash("hashed_password");
        user.setRole("OPERATOR");
        user.setEnabled(true);
    }

    @Nested
    @DisplayName("Account State Methods")
    class AccountStateTests {

        @Test
        @DisplayName("canLogin() should return true when enabled")
        void canLogin_WhenEnabled_ReturnsTrue() {
            user.setEnabled(true);

            assertThat(user.canLogin()).isTrue();
        }

        @Test
        @DisplayName("canLogin() should return false when disabled")
        void canLogin_WhenDisabled_ReturnsFalse() {
            user.setEnabled(false);

            assertThat(user.canLogin()).isFalse();
        }

        @Test
        @DisplayName("disable() should set enabled to false")
        void disable_SetsEnabledToFalse() {
            user.setEnabled(true);

            user.disable();

            assertThat(user.isEnabled()).isFalse();
            assertThat(user.canLogin()).isFalse();
        }

        @Test
        @DisplayName("enable() should set enabled to true")
        void enable_SetsEnabledToTrue() {
            user.setEnabled(false);

            user.enable();

            assertThat(user.isEnabled()).isTrue();
            assertThat(user.canLogin()).isTrue();
        }
    }

    @Nested
    @DisplayName("Role Check Methods")
    class RoleCheckTests {

        @Test
        @DisplayName("isAdmin() should return true for ADMIN role")
        void isAdmin_WhenAdmin_ReturnsTrue() {
            user.setRole("ADMIN");

            assertThat(user.isAdmin()).isTrue();
        }

        @Test
        @DisplayName("isAdmin() should be case-insensitive")
        void isAdmin_CaseInsensitive_ReturnsTrue() {
            user.setRole("admin");
            assertThat(user.isAdmin()).isTrue();

            user.setRole("Admin");
            assertThat(user.isAdmin()).isTrue();
        }

        @Test
        @DisplayName("isAdmin() should return false for non-ADMIN roles")
        void isAdmin_WhenNotAdmin_ReturnsFalse() {
            user.setRole("OPERATOR");

            assertThat(user.isAdmin()).isFalse();
        }

        @Test
        @DisplayName("isOperator() should return true for OPERATOR role")
        void isOperator_WhenOperator_ReturnsTrue() {
            user.setRole("OPERATOR");

            assertThat(user.isOperator()).isTrue();
        }

        @Test
        @DisplayName("isOperator() should be case-insensitive")
        void isOperator_CaseInsensitive_ReturnsTrue() {
            user.setRole("operator");
            assertThat(user.isOperator()).isTrue();

            user.setRole("Operator");
            assertThat(user.isOperator()).isTrue();
        }

        @Test
        @DisplayName("hasRole() should check role case-insensitively")
        void hasRole_CaseInsensitive_ReturnsTrue() {
            user.setRole("ADMIN");

            assertThat(user.hasRole("ADMIN")).isTrue();
            assertThat(user.hasRole("admin")).isTrue();
            assertThat(user.hasRole("Admin")).isTrue();
        }

        @Test
        @DisplayName("hasRole() should return false for non-matching role")
        void hasRole_NonMatchingRole_ReturnsFalse() {
            user.setRole("OPERATOR");

            assertThat(user.hasRole("ADMIN")).isFalse();
        }

        @Test
        @DisplayName("hasRole() should return false for null role name")
        void hasRole_NullRoleName_ReturnsFalse() {
            user.setRole("ADMIN");

            assertThat(user.hasRole(null)).isFalse();
        }

        @Test
        @DisplayName("hasRole() should return false when user role is null")
        void hasRole_NullUserRole_ReturnsFalse() {
            user.setRole(null);

            assertThat(user.hasRole("ADMIN")).isFalse();
        }
    }

    @Nested
    @DisplayName("Service Account Detection")
    class ServiceAccountTests {

        @Test
        @DisplayName("isServiceAccount() should return true for SERVICE role")
        void isServiceAccount_ServiceRole_ReturnsTrue() {
            user.setRole("SERVICE");

            assertThat(user.isServiceAccount()).isTrue();
        }

        @Test
        @DisplayName("isServiceAccount() should be case-insensitive for role")
        void isServiceAccount_CaseInsensitive_ReturnsTrue() {
            user.setRole("service");

            assertThat(user.isServiceAccount()).isTrue();
        }

        @Test
        @DisplayName("isServiceAccount() should return true for svc- prefixed usernames")
        void isServiceAccount_SvcPrefixedUsername_ReturnsTrue() {
            user.setUsername("svc-edge-bridge");
            user.setRole("OPERATOR");  // Even with non-SERVICE role

            assertThat(user.isServiceAccount()).isTrue();
        }

        @Test
        @DisplayName("isServiceAccount() should return false for regular users")
        void isServiceAccount_RegularUser_ReturnsFalse() {
            user.setUsername("regularuser");
            user.setRole("OPERATOR");

            assertThat(user.isServiceAccount()).isFalse();
        }
    }

    @Nested
    @DisplayName("New Account Detection")
    class NewAccountTests {

        @Test
        @DisplayName("isNewAccount() should return true when created within 24 hours")
        void isNewAccount_RecentlyCreated_ReturnsTrue() throws Exception {
            // Use reflection to set createdAt since it's private
            var field = User.class.getDeclaredField("createdAt");
            field.setAccessible(true);
            field.set(user, Instant.now().minusSeconds(3600));  // 1 hour ago

            assertThat(user.isNewAccount()).isTrue();
        }

        @Test
        @DisplayName("isNewAccount() should return false when created more than 24 hours ago")
        void isNewAccount_OldAccount_ReturnsFalse() throws Exception {
            var field = User.class.getDeclaredField("createdAt");
            field.setAccessible(true);
            field.set(user, Instant.now().minusSeconds(100000));  // More than 24 hours

            assertThat(user.isNewAccount()).isFalse();
        }

        @Test
        @DisplayName("isNewAccount() should return false when createdAt is null")
        void isNewAccount_NullCreatedAt_ReturnsFalse() {
            // Default User has null createdAt before persistence
            assertThat(user.isNewAccount()).isFalse();
        }
    }
}
