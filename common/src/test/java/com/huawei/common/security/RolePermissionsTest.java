package com.huawei.common.security;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.EnumSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RolePermissionsTest {

    @Test
    void adminHasAllPermissions() {
        Set<Permission> adminPermissions = RolePermissions.getPermissions(Roles.ADMIN);

        assertThat(adminPermissions).containsExactlyInAnyOrderElementsOf(EnumSet.allOf(Permission.class));
    }

    @Test
    void operatorHasStationCrudMetricsAlertsDiagnosticsReports() {
        Set<Permission> operatorPermissions = RolePermissions.getPermissions(Roles.OPERATOR);

        assertThat(operatorPermissions).contains(
                Permission.STATION_READ,
                Permission.STATION_CREATE,
                Permission.STATION_UPDATE,
                Permission.STATION_EXECUTE,
                Permission.METRICS_READ,
                Permission.METRICS_EXPORT,
                Permission.METRICS_CONFIGURE,
                Permission.ALERT_READ,
                Permission.ALERT_ACKNOWLEDGE,
                Permission.ALERT_RESOLVE,
                Permission.ALERT_CREATE,
                Permission.DIAGNOSTIC_READ,
                Permission.DIAGNOSTIC_EXECUTE,
                Permission.DIAGNOSTIC_APPROVE,
                Permission.DIAGNOSTIC_FEEDBACK,
                Permission.REPORT_READ,
                Permission.REPORT_GENERATE,
                Permission.REPORT_EXPORT
        );
    }

    @Test
    void operatorDoesNotHaveAdminOnlyPermissions() {
        assertThat(RolePermissions.hasPermission(Roles.OPERATOR, Permission.USER_MANAGE)).isFalse();
        assertThat(RolePermissions.hasPermission(Roles.OPERATOR, Permission.AUDIT_READ)).isFalse();
        assertThat(RolePermissions.hasPermission(Roles.OPERATOR, Permission.SYSTEM_CONFIGURE)).isFalse();
        assertThat(RolePermissions.hasPermission(Roles.OPERATOR, Permission.ACTUATOR_ACCESS)).isFalse();
        assertThat(RolePermissions.hasPermission(Roles.OPERATOR, Permission.STATION_DELETE)).isFalse();
    }

    @Test
    void userHasReadAcknowledgeAndFeedback() {
        Set<Permission> userPermissions = RolePermissions.getPermissions(Roles.USER);

        assertThat(userPermissions).contains(
                Permission.STATION_READ,
                Permission.METRICS_READ,
                Permission.ALERT_READ,
                Permission.ALERT_ACKNOWLEDGE,
                Permission.DIAGNOSTIC_READ,
                Permission.DIAGNOSTIC_FEEDBACK,
                Permission.REPORT_READ
        );
    }

    @Test
    void userDoesNotHaveCreateDeleteOrExecute() {
        assertThat(RolePermissions.hasPermission(Roles.USER, Permission.STATION_CREATE)).isFalse();
        assertThat(RolePermissions.hasPermission(Roles.USER, Permission.STATION_DELETE)).isFalse();
        assertThat(RolePermissions.hasPermission(Roles.USER, Permission.STATION_EXECUTE)).isFalse();
        assertThat(RolePermissions.hasPermission(Roles.USER, Permission.DIAGNOSTIC_EXECUTE)).isFalse();
        assertThat(RolePermissions.hasPermission(Roles.USER, Permission.ALERT_CREATE)).isFalse();
        assertThat(RolePermissions.hasPermission(Roles.USER, Permission.ALERT_RESOLVE)).isFalse();
    }

    @Test
    void viewerHasOnlyReadPermissions() {
        Set<Permission> viewerPermissions = RolePermissions.getPermissions(Roles.VIEWER);

        assertThat(viewerPermissions).containsExactlyInAnyOrder(
                Permission.STATION_READ,
                Permission.METRICS_READ,
                Permission.ALERT_READ,
                Permission.DIAGNOSTIC_READ,
                Permission.REPORT_READ
        );
    }

    @Test
    void serviceHasExpectedPermissions() {
        Set<Permission> servicePermissions = RolePermissions.getPermissions(Roles.SERVICE);

        assertThat(servicePermissions).containsExactlyInAnyOrder(
                Permission.STATION_READ,
                Permission.STATION_UPDATE,
                Permission.STATION_EXECUTE,
                Permission.METRICS_READ,
                Permission.ALERT_READ,
                Permission.ALERT_CREATE,
                Permission.DIAGNOSTIC_READ,
                Permission.DIAGNOSTIC_EXECUTE
        );
    }

    @Test
    void hasPermissionWithNullRoleReturnsFalse() {
        assertThat(RolePermissions.hasPermission(null, Permission.STATION_READ)).isFalse();
    }

    @Test
    void hasPermissionWithNullPermissionReturnsFalse() {
        assertThat(RolePermissions.hasPermission(Roles.ADMIN, null)).isFalse();
    }

    @Test
    void hasPermissionWithRolePrefixStillWorks() {
        assertThat(RolePermissions.hasPermission("ROLE_ADMIN", Permission.STATION_READ)).isTrue();
        assertThat(RolePermissions.hasPermission("ROLE_OPERATOR", Permission.METRICS_READ)).isTrue();
        assertThat(RolePermissions.hasPermission("ROLE_USER", Permission.ALERT_READ)).isTrue();
        assertThat(RolePermissions.hasPermission("ROLE_VIEWER", Permission.STATION_READ)).isTrue();
        assertThat(RolePermissions.hasPermission("ROLE_SERVICE", Permission.DIAGNOSTIC_EXECUTE)).isTrue();
    }

    @Test
    void getPermissionsReturnsEmptySetForUnknownRole() {
        Set<Permission> permissions = RolePermissions.getPermissions("NONEXISTENT");

        assertThat(permissions).isEmpty();
    }

    @Test
    void getPermissionsReturnsImmutableSet() {
        Set<Permission> adminPermissions = RolePermissions.getPermissions(Roles.ADMIN);

        assertThatThrownBy(() -> adminPermissions.add(Permission.STATION_READ))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void getPermissionsForNullReturnsEmptySet() {
        Set<Permission> permissions = RolePermissions.getPermissions(null);

        assertThat(permissions).isEmpty();
    }

    @Test
    void hasAnyPermissionReturnsTrueIfAnyMatch() {
        assertThat(RolePermissions.hasAnyPermission(
                Roles.VIEWER,
                Permission.STATION_DELETE,
                Permission.STATION_READ
        )).isTrue();
    }

    @Test
    void hasAnyPermissionReturnsFalseIfNoneMatch() {
        assertThat(RolePermissions.hasAnyPermission(
                Roles.VIEWER,
                Permission.STATION_DELETE,
                Permission.USER_MANAGE
        )).isFalse();
    }

    @Test
    void hasAllPermissionsReturnsTrueIfAllPresent() {
        assertThat(RolePermissions.hasAllPermissions(
                Roles.ADMIN,
                Permission.STATION_READ,
                Permission.USER_MANAGE,
                Permission.ACTUATOR_ACCESS
        )).isTrue();
    }

    @Test
    void hasAllPermissionsReturnsFalseIfAnyMissing() {
        assertThat(RolePermissions.hasAllPermissions(
                Roles.OPERATOR,
                Permission.STATION_READ,
                Permission.USER_MANAGE
        )).isFalse();
    }

    @Test
    void cannotInstantiateRolePermissions() throws Exception {
        Constructor<RolePermissions> constructor = RolePermissions.class.getDeclaredConstructor();
        constructor.setAccessible(true);

        assertThatThrownBy(constructor::newInstance)
                .isInstanceOf(InvocationTargetException.class)
                .hasCauseInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void hasPermissionIsCaseInsensitive() {
        assertThat(RolePermissions.hasPermission("admin", Permission.STATION_READ)).isTrue();
        assertThat(RolePermissions.hasPermission("Admin", Permission.STATION_READ)).isTrue();
        assertThat(RolePermissions.hasPermission("role_admin", Permission.STATION_READ)).isTrue();
    }
}
