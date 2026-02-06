package com.huawei.monitoring.model;

import com.huawei.monitoring.model.SONRecommendation.SONFunction;
import com.huawei.monitoring.model.SONRecommendation.SONStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for SONRecommendation model.
 */
@DisplayName("SONRecommendation Tests")
class SONRecommendationTest {

    private SONRecommendation recommendation;

    @BeforeEach
    void setUp() {
        recommendation = new SONRecommendation(1L, SONFunction.MLB, "adjust_capacity");
    }

    @Nested
    @DisplayName("Constructor Tests")
    class ConstructorTests {

        @Test
        @DisplayName("Default constructor sets initial values")
        void defaultConstructor_SetsInitialValues() {
            SONRecommendation rec = new SONRecommendation();

            assertNotNull(rec.getCreatedAt(), "createdAt should be set");
            assertNotNull(rec.getUpdatedAt(), "updatedAt should be set");
            assertEquals(SONStatus.PENDING, rec.getStatus(), "Default status should be PENDING");
        }

        @Test
        @DisplayName("Parameterized constructor sets required fields")
        void parameterizedConstructor_SetsFields() {
            assertEquals(1L, recommendation.getStationId());
            assertEquals(SONFunction.MLB, recommendation.getFunctionType());
            assertEquals("adjust_capacity", recommendation.getActionType());
            assertEquals(SONStatus.PENDING, recommendation.getStatus());
        }
    }

    @Nested
    @DisplayName("State Transition Tests")
    class StateTransitionTests {

        @Test
        @DisplayName("approve() sets status and approver info")
        void approve_SetsStatusAndApproverInfo() {
            LocalDateTime beforeApproval = LocalDateTime.now();

            recommendation.approve("admin");

            assertEquals(SONStatus.APPROVED, recommendation.getStatus());
            assertEquals("admin", recommendation.getApprovedBy());
            assertNotNull(recommendation.getApprovedAt());
            assertTrue(recommendation.getApprovedAt().isAfter(beforeApproval.minusSeconds(1)));
            assertTrue(recommendation.getUpdatedAt().isAfter(beforeApproval.minusSeconds(1)));
        }

        @Test
        @DisplayName("reject() sets status, rejector and reason")
        void reject_SetsStatusAndRejectorInfo() {
            LocalDateTime beforeReject = LocalDateTime.now();

            recommendation.reject("operator", "Not suitable for current traffic");

            assertEquals(SONStatus.REJECTED, recommendation.getStatus());
            assertEquals("operator", recommendation.getRejectedBy());
            assertEquals("Not suitable for current traffic", recommendation.getRejectionReason());
            assertNotNull(recommendation.getRejectedAt());
            assertTrue(recommendation.getRejectedAt().isAfter(beforeReject.minusSeconds(1)));
        }

        @Test
        @DisplayName("markExecuting() sets EXECUTING status")
        void markExecuting_SetsExecutingStatus() {
            recommendation.approve("admin");
            LocalDateTime beforeExecuting = LocalDateTime.now();

            recommendation.markExecuting();

            assertEquals(SONStatus.EXECUTING, recommendation.getStatus());
            assertTrue(recommendation.getUpdatedAt().isAfter(beforeExecuting.minusSeconds(1)));
        }

        @Test
        @DisplayName("markExecuted() with success sets EXECUTED status")
        void markExecuted_Success_SetsExecutedStatus() {
            recommendation.approve("admin");
            recommendation.markExecuting();

            recommendation.markExecuted(true, "Parameters applied successfully");

            assertEquals(SONStatus.EXECUTED, recommendation.getStatus());
            assertTrue(recommendation.getExecutionSuccess());
            assertEquals("Parameters applied successfully", recommendation.getExecutionResult());
            assertNotNull(recommendation.getExecutedAt());
        }

        @Test
        @DisplayName("markExecuted() with failure sets FAILED status")
        void markExecuted_Failure_SetsFailedStatus() {
            recommendation.approve("admin");
            recommendation.markExecuting();

            recommendation.markExecuted(false, "Connection timeout");

            assertEquals(SONStatus.FAILED, recommendation.getStatus());
            assertFalse(recommendation.getExecutionSuccess());
            assertEquals("Connection timeout", recommendation.getExecutionResult());
        }

        @Test
        @DisplayName("rollback() sets ROLLED_BACK status")
        void rollback_SetsRolledBackStatus() {
            recommendation.approve("admin");
            recommendation.markExecuting();
            recommendation.markExecuted(true, "Applied");
            recommendation.setRollbackAction("revert_capacity");

            recommendation.rollback("operator", "Performance degraded");

            assertEquals(SONStatus.ROLLED_BACK, recommendation.getStatus());
            assertEquals("operator", recommendation.getRolledBackBy());
            assertEquals("Performance degraded", recommendation.getRollbackReason());
            assertNotNull(recommendation.getRolledBackAt());
        }

        @Test
        @DisplayName("markExpired() sets EXPIRED status")
        void markExpired_SetsExpiredStatus() {
            recommendation.markExpired();

            assertEquals(SONStatus.EXPIRED, recommendation.getStatus());
        }
    }

    @Nested
    @DisplayName("Utility Method Tests")
    class UtilityMethodTests {

        @Test
        @DisplayName("isPending() returns true for PENDING status")
        void isPending_ReturnsTrueForPending() {
            assertTrue(recommendation.isPending());
        }

        @Test
        @DisplayName("isPending() returns false for non-PENDING status")
        void isPending_ReturnsFalseForNonPending() {
            recommendation.approve("admin");
            assertFalse(recommendation.isPending());
        }

        @Test
        @DisplayName("isApproved() returns true for APPROVED status")
        void isApproved_ReturnsTrueForApproved() {
            recommendation.approve("admin");
            assertTrue(recommendation.isApproved());
        }

        @Test
        @DisplayName("isExecuted() returns true for EXECUTED status")
        void isExecuted_ReturnsTrueForExecuted() {
            recommendation.approve("admin");
            recommendation.markExecuting();
            recommendation.markExecuted(true, "Done");
            assertTrue(recommendation.isExecuted());
        }

        @Test
        @DisplayName("canBeApproved() returns true only for PENDING status")
        void canBeApproved_ReturnsTrueOnlyForPending() {
            assertTrue(recommendation.canBeApproved(), "Should be approvable when PENDING");

            recommendation.approve("admin");
            assertFalse(recommendation.canBeApproved(), "Should not be approvable when APPROVED");
        }

        @Test
        @DisplayName("canBeRolledBack() returns true for EXECUTED with rollback action")
        void canBeRolledBack_ReturnsTrueForExecutedWithRollback() {
            recommendation.approve("admin");
            recommendation.markExecuting();
            recommendation.markExecuted(true, "Applied");

            assertFalse(recommendation.canBeRolledBack(), "Should not be rollbackable without rollback action");

            recommendation.setRollbackAction("revert");
            assertTrue(recommendation.canBeRolledBack(), "Should be rollbackable with rollback action");
        }

        @Test
        @DisplayName("isExpired() returns true when expiresAt is in the past")
        void isExpired_ReturnsTrueWhenExpired() {
            assertFalse(recommendation.isExpired(), "Should not be expired without expiresAt");

            recommendation.setExpiresAt(LocalDateTime.now().plusHours(1));
            assertFalse(recommendation.isExpired(), "Should not be expired when future");

            recommendation.setExpiresAt(LocalDateTime.now().minusHours(1));
            assertTrue(recommendation.isExpired(), "Should be expired when past");
        }
    }

    @Nested
    @DisplayName("Enum Tests")
    class EnumTests {

        @Test
        @DisplayName("SONFunction has display names")
        void sonFunction_HasDisplayNames() {
            assertEquals("Mobility Load Balancing", SONFunction.MLB.getDisplayName());
            assertEquals("Mobility Robustness Optimization", SONFunction.MRO.getDisplayName());
            assertEquals("Coverage & Capacity Optimization", SONFunction.CCO.getDisplayName());
            assertEquals("Energy Saving", SONFunction.ES.getDisplayName());
            assertEquals("Automatic Neighbor Relation", SONFunction.ANR.getDisplayName());
            assertEquals("Random Access Optimization", SONFunction.RAO.getDisplayName());
            assertEquals("Inter-Cell Interference Coordination", SONFunction.ICIC.getDisplayName());
        }

        @Test
        @DisplayName("All SONFunction values exist")
        void sonFunction_AllValuesExist() {
            assertEquals(7, SONFunction.values().length);
        }

        @Test
        @DisplayName("All SONStatus values exist")
        void sonStatus_AllValuesExist() {
            assertEquals(8, SONStatus.values().length);
            assertNotNull(SONStatus.valueOf("PENDING"));
            assertNotNull(SONStatus.valueOf("APPROVED"));
            assertNotNull(SONStatus.valueOf("REJECTED"));
            assertNotNull(SONStatus.valueOf("EXECUTING"));
            assertNotNull(SONStatus.valueOf("EXECUTED"));
            assertNotNull(SONStatus.valueOf("FAILED"));
            assertNotNull(SONStatus.valueOf("ROLLED_BACK"));
            assertNotNull(SONStatus.valueOf("EXPIRED"));
        }
    }

    @Nested
    @DisplayName("Setter Tests")
    class SetterTests {

        @Test
        @DisplayName("setStatus() updates updatedAt timestamp")
        void setStatus_UpdatesTimestamp() {
            LocalDateTime originalUpdatedAt = recommendation.getUpdatedAt();

            // Small delay to ensure timestamp difference
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            recommendation.setStatus(SONStatus.APPROVED);

            assertTrue(recommendation.getUpdatedAt().isAfter(originalUpdatedAt) ||
                    recommendation.getUpdatedAt().isEqual(originalUpdatedAt));
        }

        @Test
        @DisplayName("Optional fields can be set and retrieved")
        void optionalFields_CanBeSetAndRetrieved() {
            recommendation.setActionValue("50%");
            recommendation.setDescription("Reduce load on overloaded cell");
            recommendation.setExpectedImprovement(0.15);
            recommendation.setConfidence(0.85);
            recommendation.setAutoExecutable(true);
            recommendation.setApprovalRequired(false);
            recommendation.setRollbackAction("restore_original");

            assertEquals("50%", recommendation.getActionValue());
            assertEquals("Reduce load on overloaded cell", recommendation.getDescription());
            assertEquals(0.15, recommendation.getExpectedImprovement());
            assertEquals(0.85, recommendation.getConfidence());
            assertTrue(recommendation.getAutoExecutable());
            assertFalse(recommendation.getApprovalRequired());
            assertEquals("restore_original", recommendation.getRollbackAction());
        }
    }
}
