package io.github.erselseyit.basestation.common.security;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The role predicates are documented as case-insensitive. They must agree with
 * each other about that — a check that is lenient for one role and strict for
 * another is a security-relevant inconsistency, not a cosmetic one.
 */
class RolesTest {

    @Nested
    class Admin {

        @ParameterizedTest
        @ValueSource(strings = {"ADMIN", "admin", "Admin", "ROLE_ADMIN", "role_admin"})
        void recognisesAdminInAnyCase(String role) {
            assertThat(Roles.isAdmin(role)).isTrue();
        }

        @ParameterizedTest
        @ValueSource(strings = {"OPERATOR", "USER", "SERVICE", "", "administrator"})
        void rejectsNonAdminRoles(String role) {
            assertThat(Roles.isAdmin(role)).isFalse();
        }

        @Test
        void isNullSafe() {
            assertThat(Roles.isAdmin(null)).isFalse();
        }
    }

    @Nested
    class OperatorOrHigher {

        @ParameterizedTest
        @ValueSource(strings = {"ADMIN", "admin", "OPERATOR", "operator", "ROLE_OPERATOR", "role_operator"})
        void recognisesOperatorOrAdminInAnyCase(String role) {
            assertThat(Roles.isOperatorOrHigher(role)).isTrue();
        }

        @ParameterizedTest
        @ValueSource(strings = {"USER", "SERVICE", "guest"})
        void rejectsLesserRoles(String role) {
            assertThat(Roles.isOperatorOrHigher(role)).isFalse();
        }

        @Test
        void isNullSafe() {
            assertThat(Roles.isOperatorOrHigher(null)).isFalse();
        }
    }

    @Nested
    class ValidRole {

        @ParameterizedTest
        @ValueSource(strings = {
                "ADMIN", "admin",
                "OPERATOR", "operator",
                "USER", "user",
                "SERVICE", "service",
                "ROLE_ADMIN", "role_admin",
                "ROLE_USER", "role_user",
                "ROLE_SERVICE", "role_service"})
        void acceptsEveryKnownRoleInAnyCase(String role) {
            assertThat(Roles.isValidRole(role)).isTrue();
        }

        @ParameterizedTest
        @ValueSource(strings = {"", "guest", "superuser", "ROLE_GUEST"})
        void rejectsUnknownRoles(String role) {
            assertThat(Roles.isValidRole(role)).isFalse();
        }

        @Test
        void isNullSafe() {
            assertThat(Roles.isValidRole(null)).isFalse();
        }
    }
}
