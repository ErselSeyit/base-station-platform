package com.huawei.monitoring.service;

import com.huawei.monitoring.model.SONRecommendation;
import com.huawei.monitoring.model.SONRecommendation.SONFunction;
import com.huawei.monitoring.model.SONRecommendation.SONStatus;
import com.huawei.monitoring.repository.SONRecommendationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for SONService.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SONService Tests")
@SuppressWarnings("null") // Mockito mocks and test assertions handle null safely
class SONServiceTest {

    @Mock
    private SONRecommendationRepository repository;

    @Mock
    private RestClient.Builder restClientBuilder;

    @Mock
    private RestClient restClient;

    private SONService sonService;

    @BeforeEach
    void setUp() {
        when(restClientBuilder.build()).thenReturn(restClient);
        sonService = new SONService(repository, restClientBuilder);
    }

    private SONRecommendation createTestRecommendation() {
        SONRecommendation rec = new SONRecommendation(1L, SONFunction.MLB, "adjust_capacity");
        rec.setId("test-id-123");
        rec.setDescription("Test recommendation");
        rec.setConfidence(0.85);
        return rec;
    }

    @Nested
    @DisplayName("CRUD Operations")
    class CrudOperations {

        @Test
        @DisplayName("create() sets timestamps and saves recommendation")
        void create_SetsTimestampsAndSaves() {
            SONRecommendation input = new SONRecommendation(1L, SONFunction.MLB, "adjust");
            when(repository.save(any(SONRecommendation.class))).thenAnswer(inv -> {
                SONRecommendation saved = inv.getArgument(0);
                saved.setId("generated-id");
                return saved;
            });

            SONRecommendation result = sonService.create(input);

            assertNotNull(result.getCreatedAt());
            assertNotNull(result.getUpdatedAt());
            assertNotNull(result.getExpiresAt()); // Default 24h expiry
            verify(repository).save(input);
        }

        @Test
        @DisplayName("getById() returns Optional from repository")
        void getById_ReturnsOptional() {
            SONRecommendation rec = createTestRecommendation();
            when(repository.findById("test-id")).thenReturn(Optional.of(rec));

            Optional<SONRecommendation> result = sonService.getById("test-id");

            assertTrue(result.isPresent());
            assertEquals("test-id-123", result.get().getId());
        }

        @Test
        @DisplayName("getAll() returns list from repository")
        void getAll_ReturnsList() {
            List<SONRecommendation> recommendations = List.of(
                    createTestRecommendation(),
                    createTestRecommendation()
            );
            when(repository.findAll()).thenReturn(recommendations);

            List<SONRecommendation> result = sonService.getAll();

            assertEquals(2, result.size());
        }

        @Test
        @DisplayName("getAll(Pageable) returns page from repository")
        void getAll_WithPageable_ReturnsPage() {
            Pageable pageable = PageRequest.of(0, 10);
            Page<SONRecommendation> page = new PageImpl<>(List.of(createTestRecommendation()));
            when(repository.findAll(pageable)).thenReturn(page);

            Page<SONRecommendation> result = sonService.getAll(pageable);

            assertEquals(1, result.getTotalElements());
        }

        @Test
        @DisplayName("getByStation() returns recommendations for station")
        void getByStation_ReturnsForStation() {
            List<SONRecommendation> recommendations = List.of(createTestRecommendation());
            when(repository.findByStationId(1L)).thenReturn(recommendations);

            List<SONRecommendation> result = sonService.getByStation(1L);

            assertEquals(1, result.size());
            assertEquals(1L, result.get(0).getStationId());
        }

        @Test
        @DisplayName("getPending() returns pending recommendations")
        void getPending_ReturnsPending() {
            List<SONRecommendation> recommendations = List.of(createTestRecommendation());
            when(repository.findByStatus(SONStatus.PENDING)).thenReturn(recommendations);

            List<SONRecommendation> result = sonService.getPending();

            assertEquals(1, result.size());
        }

        @Test
        @DisplayName("getByFunctionType() returns by function")
        void getByFunctionType_ReturnsByFunction() {
            List<SONRecommendation> recommendations = List.of(createTestRecommendation());
            when(repository.findByFunctionType(SONFunction.MLB)).thenReturn(recommendations);

            List<SONRecommendation> result = sonService.getByFunctionType(SONFunction.MLB);

            assertEquals(1, result.size());
        }
    }

    @Nested
    @DisplayName("Approval Workflow")
    class ApprovalWorkflow {

        @Test
        @DisplayName("approve() updates status and saves")
        void approve_UpdatesStatusAndSaves() {
            SONRecommendation rec = createTestRecommendation();
            when(repository.findById("test-id")).thenReturn(Optional.of(rec));
            when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            // Mock the RestClient chain for createDeviceCommand
            // The RestClient call may fail in tests since we don't have a real service
            // The approve() still succeeds even if createDeviceCommand fails
            RestClient.RequestBodyUriSpec requestSpec = mock(RestClient.RequestBodyUriSpec.class, withSettings().strictness(org.mockito.quality.Strictness.LENIENT));
            when(restClient.post()).thenReturn(requestSpec);
            when(requestSpec.uri(anyString(), any(Object.class))).thenThrow(new RuntimeException("Test - no real service"));

            Optional<SONRecommendation> result = sonService.approve("test-id", "admin");

            assertTrue(result.isPresent());
            assertEquals(SONStatus.APPROVED, result.get().getStatus());
            assertEquals("admin", result.get().getApprovedBy());

            ArgumentCaptor<SONRecommendation> captor = ArgumentCaptor.forClass(SONRecommendation.class);
            verify(repository).save(captor.capture());
            assertEquals(SONStatus.APPROVED, captor.getValue().getStatus());
        }

        @Test
        @DisplayName("approve() returns empty when not found")
        void approve_ReturnsEmpty_WhenNotFound() {
            when(repository.findById("unknown")).thenReturn(Optional.empty());

            Optional<SONRecommendation> result = sonService.approve("unknown", "admin");

            assertTrue(result.isEmpty());
            verify(repository, never()).save(any());
        }

        @Test
        @DisplayName("approve() returns empty when not pending")
        void approve_ReturnsEmpty_WhenNotPending() {
            SONRecommendation rec = createTestRecommendation();
            rec.approve("someone"); // Already approved
            when(repository.findById("test-id")).thenReturn(Optional.of(rec));

            Optional<SONRecommendation> result = sonService.approve("test-id", "admin");

            assertTrue(result.isEmpty());
            verify(repository, never()).save(any());
        }

        @Test
        @DisplayName("reject() updates status with reason")
        void reject_UpdatesStatusWithReason() {
            SONRecommendation rec = createTestRecommendation();
            when(repository.findById("test-id")).thenReturn(Optional.of(rec));
            when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            Optional<SONRecommendation> result = sonService.reject("test-id", "operator", "Not suitable");

            assertTrue(result.isPresent());
            assertEquals(SONStatus.REJECTED, result.get().getStatus());
            assertEquals("operator", result.get().getRejectedBy());
            assertEquals("Not suitable", result.get().getRejectionReason());
        }

        @Test
        @DisplayName("markExecuting() transitions APPROVED to EXECUTING")
        void markExecuting_TransitionsToExecuting() {
            SONRecommendation rec = createTestRecommendation();
            rec.approve("admin");
            when(repository.findById("test-id")).thenReturn(Optional.of(rec));
            when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            Optional<SONRecommendation> result = sonService.markExecuting("test-id");

            assertTrue(result.isPresent());
            assertEquals(SONStatus.EXECUTING, result.get().getStatus());
        }

        @Test
        @DisplayName("markExecuting() returns empty when not approved")
        void markExecuting_ReturnsEmpty_WhenNotApproved() {
            SONRecommendation rec = createTestRecommendation(); // PENDING status
            when(repository.findById("test-id")).thenReturn(Optional.of(rec));

            Optional<SONRecommendation> result = sonService.markExecuting("test-id");

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("recordExecutionResult() records success")
        void recordExecutionResult_RecordsSuccess() {
            SONRecommendation rec = createTestRecommendation();
            rec.approve("admin");
            rec.markExecuting();
            when(repository.findById("test-id")).thenReturn(Optional.of(rec));
            when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            Optional<SONRecommendation> result = sonService.recordExecutionResult("test-id", true, "Applied");

            assertTrue(result.isPresent());
            assertEquals(SONStatus.EXECUTED, result.get().getStatus());
            assertTrue(result.get().getExecutionSuccess());
            assertEquals("Applied", result.get().getExecutionResult());
        }

        @Test
        @DisplayName("recordExecutionResult() records failure")
        void recordExecutionResult_RecordsFailure() {
            SONRecommendation rec = createTestRecommendation();
            rec.approve("admin");
            rec.markExecuting();
            when(repository.findById("test-id")).thenReturn(Optional.of(rec));
            when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            Optional<SONRecommendation> result = sonService.recordExecutionResult("test-id", false, "Timeout");

            assertTrue(result.isPresent());
            assertEquals(SONStatus.FAILED, result.get().getStatus());
            assertFalse(result.get().getExecutionSuccess());
        }

        @Test
        @DisplayName("rollback() transitions EXECUTED to ROLLED_BACK")
        void rollback_TransitionsToRolledBack() {
            SONRecommendation rec = createTestRecommendation();
            rec.approve("admin");
            rec.markExecuting();
            rec.markExecuted(true, "Applied");
            rec.setRollbackAction("revert");
            when(repository.findById("test-id")).thenReturn(Optional.of(rec));
            when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            Optional<SONRecommendation> result = sonService.rollback("test-id", "operator", "Performance issue");

            assertTrue(result.isPresent());
            assertEquals(SONStatus.ROLLED_BACK, result.get().getStatus());
            assertEquals("operator", result.get().getRolledBackBy());
            assertEquals("Performance issue", result.get().getRollbackReason());
        }

        @Test
        @DisplayName("rollback() returns empty when cannot be rolled back")
        void rollback_ReturnsEmpty_WhenCannotRollback() {
            SONRecommendation rec = createTestRecommendation(); // PENDING, no rollback action
            when(repository.findById("test-id")).thenReturn(Optional.of(rec));

            Optional<SONRecommendation> result = sonService.rollback("test-id", "operator", "Reason");

            assertTrue(result.isEmpty());
        }
    }

    @Nested
    @DisplayName("Statistics")
    class Statistics {

        @Test
        @DisplayName("getStats() returns aggregated statistics")
        void getStats_ReturnsAggregatedStats() {
            when(repository.countByStatus(SONStatus.PENDING)).thenReturn(5L);
            when(repository.countByStatus(SONStatus.APPROVED)).thenReturn(3L);
            when(repository.countByStatus(SONStatus.EXECUTED)).thenReturn(10L);
            when(repository.countByStatus(SONStatus.FAILED)).thenReturn(2L);
            when(repository.countByStatus(SONStatus.REJECTED)).thenReturn(1L);
            when(repository.countByStatus(SONStatus.ROLLED_BACK)).thenReturn(1L);

            Map<String, Object> stats = sonService.getStats();

            assertEquals(5L, stats.get("pending"));
            assertEquals(3L, stats.get("approved"));
            assertEquals(10L, stats.get("executed"));
            assertEquals(2L, stats.get("failed"));
            assertEquals(1L, stats.get("rejected"));
            assertEquals(1L, stats.get("rolledBack"));
            assertEquals(22L, stats.get("total"));
            // 10 executed / 22 total = 45.45%
            assertEquals("45.5%", stats.get("successRate"));
        }

        @Test
        @DisplayName("getStats() handles zero total")
        void getStats_HandlesZeroTotal() {
            when(repository.countByStatus(any())).thenReturn(0L);

            Map<String, Object> stats = sonService.getStats();

            assertEquals(0L, stats.get("total"));
            assertEquals("0.0%", stats.get("successRate"));
        }

        @Test
        @DisplayName("getStatsForStation() returns station-specific stats")
        void getStatsForStation_ReturnsStationStats() {
            when(repository.countByStationIdAndStatus(1L, SONStatus.PENDING)).thenReturn(2L);
            when(repository.countByStationIdAndStatus(1L, SONStatus.APPROVED)).thenReturn(1L);
            when(repository.countByStationIdAndStatus(1L, SONStatus.EXECUTED)).thenReturn(5L);
            when(repository.countByStationIdAndStatus(1L, SONStatus.FAILED)).thenReturn(1L);

            Map<String, Object> stats = sonService.getStatsForStation(1L);

            assertEquals(1L, stats.get("stationId"));
            assertEquals(2L, stats.get("pending"));
            assertEquals(1L, stats.get("approved"));
            assertEquals(5L, stats.get("executed"));
            assertEquals(1L, stats.get("failed"));
        }
    }

    @Nested
    @DisplayName("Query Methods")
    class QueryMethods {

        @Test
        @DisplayName("getByStatus() returns by status")
        void getByStatus_ReturnsByStatus() {
            List<SONRecommendation> recommendations = List.of(createTestRecommendation());
            when(repository.findByStatus(SONStatus.PENDING)).thenReturn(recommendations);

            List<SONRecommendation> result = sonService.getByStatus(SONStatus.PENDING);

            assertEquals(1, result.size());
        }

        @Test
        @DisplayName("getByStatus(Pageable) returns paged results")
        void getByStatus_WithPageable_ReturnsPaged() {
            Pageable pageable = PageRequest.of(0, 10);
            Page<SONRecommendation> page = new PageImpl<>(List.of(createTestRecommendation()));
            when(repository.findByStatus(SONStatus.PENDING, pageable)).thenReturn(page);

            Page<SONRecommendation> result = sonService.getByStatus(SONStatus.PENDING, pageable);

            assertEquals(1, result.getTotalElements());
        }

        @Test
        @DisplayName("getPendingForStation() returns pending for station")
        void getPendingForStation_ReturnsPendingForStation() {
            List<SONRecommendation> recommendations = List.of(createTestRecommendation());
            when(repository.findByStationIdAndStatus(1L, SONStatus.PENDING)).thenReturn(recommendations);

            List<SONRecommendation> result = sonService.getPendingForStation(1L);

            assertEquals(1, result.size());
            verify(repository).findByStationIdAndStatus(1L, SONStatus.PENDING);
        }
    }
}
