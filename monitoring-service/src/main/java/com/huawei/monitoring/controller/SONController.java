package com.huawei.monitoring.controller;

import com.huawei.monitoring.model.SONRecommendation;
import com.huawei.monitoring.model.SONRecommendation.SONFunction;
import com.huawei.monitoring.model.SONRecommendation.SONStatus;
import com.huawei.monitoring.service.SONService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.huawei.common.constants.ValidationMessages;
import com.huawei.common.security.Roles;

/**
 * REST controller for SON (Self-Organizing Network) recommendations.
 *
 * Provides endpoints for viewing, approving, rejecting, and managing
 * AI-generated network optimization recommendations.
 */
@RestController
@RequestMapping("/api/v1/son")
@Tag(name = "SON Recommendations", description = "Self-Organizing Network recommendation management")
@PreAuthorize("isAuthenticated()")
@SuppressWarnings("null") // Spring Security and validation framework guarantee non-null
public class SONController {

    private static final Logger log = LoggerFactory.getLogger(SONController.class);

    private final SONService sonService;

    public SONController(SONService sonService) {
        this.sonService = sonService;
    }

    // ========================================================================
    // Query Endpoints
    // ========================================================================

    @Operation(
            summary = "Get all recommendations",
            description = "Returns all SON recommendations with optional pagination.")
    @ApiResponse(responseCode = "200", description = "Recommendations retrieved")
    @GetMapping
    public ResponseEntity<Page<SONRecommendation>> getAll(Pageable pageable) {
        return ResponseEntity.ok(sonService.getAll(pageable));
    }

    @Operation(
            summary = "Get pending recommendations",
            description = "Returns all pending recommendations awaiting approval.")
    @ApiResponse(responseCode = "200", description = "Pending recommendations retrieved")
    @GetMapping("/pending")
    public ResponseEntity<List<SONRecommendation>> getPending() {
        return ResponseEntity.ok(sonService.getPending());
    }

    @Operation(
            summary = "Get recommendation by ID",
            description = "Returns a specific recommendation by its ID.")
    @ApiResponse(responseCode = "200", description = "Recommendation found")
    @ApiResponse(responseCode = "404", description = "Recommendation not found")
    @GetMapping("/{id}")
    public ResponseEntity<SONRecommendation> getById(
            @Parameter(description = "Recommendation ID")
            @PathVariable String id) {
        return Objects.requireNonNull(
                sonService.getById(id)
                        .map(ResponseEntity::ok)
                        .orElseGet(() -> ResponseEntity.notFound().build()),
                ValidationMessages.RESPONSE_NULL_MESSAGE);
    }

    @Operation(
            summary = "Get recommendations by status",
            description = "Returns all recommendations with the specified status.")
    @ApiResponse(responseCode = "200", description = "Recommendations retrieved")
    @GetMapping("/status/{status}")
    public ResponseEntity<Page<SONRecommendation>> getByStatus(
            @Parameter(description = "Recommendation status")
            @PathVariable SONStatus status,
            Pageable pageable) {
        return ResponseEntity.ok(sonService.getByStatus(status, pageable));
    }

    @Operation(
            summary = "Get recommendations for station",
            description = "Returns all recommendations for a specific station.")
    @ApiResponse(responseCode = "200", description = "Recommendations retrieved")
    @GetMapping("/station/{stationId}")
    public ResponseEntity<List<SONRecommendation>> getByStation(
            @Parameter(description = "Station ID")
            @PathVariable Long stationId) {
        return ResponseEntity.ok(sonService.getByStation(stationId));
    }

    @Operation(
            summary = "Get pending recommendations for station",
            description = "Returns pending recommendations for a specific station.")
    @ApiResponse(responseCode = "200", description = "Pending recommendations retrieved")
    @GetMapping("/station/{stationId}/pending")
    public ResponseEntity<List<SONRecommendation>> getPendingForStation(
            @Parameter(description = "Station ID")
            @PathVariable Long stationId) {
        return ResponseEntity.ok(sonService.getPendingForStation(stationId));
    }

    @Operation(
            summary = "Get recommendations by function type",
            description = "Returns all recommendations for a specific SON function.")
    @ApiResponse(responseCode = "200", description = "Recommendations retrieved")
    @GetMapping("/function/{functionType}")
    public ResponseEntity<List<SONRecommendation>> getByFunctionType(
            @Parameter(description = "SON function type")
            @PathVariable SONFunction functionType) {
        return ResponseEntity.ok(sonService.getByFunctionType(functionType));
    }

    // ========================================================================
    // Approval Workflow Endpoints
    // ========================================================================

    @Operation(
            summary = "Approve recommendation",
            description = "Approves a pending recommendation for execution.")
    @ApiResponse(responseCode = "200", description = "Recommendation approved")
    @ApiResponse(responseCode = "404", description = "Recommendation not found or not pending")
    @PostMapping("/{id}/approve")
    @PreAuthorize(Roles.HAS_OPERATOR)
    public ResponseEntity<SONRecommendation> approve(
            @Parameter(description = "Recommendation ID")
            @PathVariable String id,
            @AuthenticationPrincipal @Nullable UserDetails user) {

        String username = getUsername(user);

        return Objects.requireNonNull(
                sonService.approve(id, username)
                        .map(rec -> {
                            log.info("SON recommendation {} approved by {}", id, username);
                            return ResponseEntity.ok(rec);
                        })
                        .orElseGet(() -> ResponseEntity.notFound().build()),
                ValidationMessages.RESPONSE_NULL_MESSAGE);
    }

    @Operation(
            summary = "Reject recommendation",
            description = "Rejects a pending recommendation with a reason.")
    @ApiResponse(responseCode = "200", description = "Recommendation rejected")
    @ApiResponse(responseCode = "404", description = "Recommendation not found or not pending")
    @PostMapping("/{id}/reject")
    @PreAuthorize(Roles.HAS_OPERATOR)
    public ResponseEntity<SONRecommendation> reject(
            @Parameter(description = "Recommendation ID")
            @PathVariable String id,
            @Valid @RequestBody RejectRequest request,
            @AuthenticationPrincipal @Nullable UserDetails user) {

        String username = getUsername(user);

        return Objects.requireNonNull(
                sonService.reject(id, username, request.reason())
                        .map(rec -> {
                            log.info("SON recommendation {} rejected by {}: {}",
                                    id, username, request.reason());
                            return ResponseEntity.ok(rec);
                        })
                        .orElseGet(() -> ResponseEntity.notFound().build()),
                ValidationMessages.RESPONSE_NULL_MESSAGE);
    }

    @Operation(
            summary = "Rollback recommendation",
            description = "Rolls back an executed recommendation to its previous state.")
    @ApiResponse(responseCode = "200", description = "Recommendation rolled back")
    @ApiResponse(responseCode = "404", description = "Recommendation not found or cannot be rolled back")
    @PostMapping("/{id}/rollback")
    @PreAuthorize(Roles.HAS_OPERATOR)
    public ResponseEntity<SONRecommendation> rollback(
            @Parameter(description = "Recommendation ID")
            @PathVariable String id,
            @Valid @RequestBody RollbackRequest request,
            @AuthenticationPrincipal @Nullable UserDetails user) {

        String username = getUsername(user);

        return Objects.requireNonNull(
                sonService.rollback(id, username, request.reason())
                        .map(rec -> {
                            log.info("SON recommendation {} rolled back by {}: {}",
                                    id, username, request.reason());
                            return ResponseEntity.ok(rec);
                        })
                        .orElseGet(() -> ResponseEntity.notFound().build()),
                ValidationMessages.RESPONSE_NULL_MESSAGE);
    }

    // ========================================================================
    // Execution Callback Endpoints
    // ========================================================================

    @Operation(
            summary = "Record execution start",
            description = "Marks a recommendation as currently executing.")
    @ApiResponse(responseCode = "200", description = "Execution started")
    @ApiResponse(responseCode = "404", description = "Recommendation not found or not approved")
    @PostMapping("/{id}/execute/start")
    public ResponseEntity<SONRecommendation> startExecution(
            @Parameter(description = "Recommendation ID")
            @PathVariable String id) {

        return Objects.requireNonNull(
                sonService.markExecuting(id)
                        .map(rec -> {
                            log.info("SON recommendation {} execution started", id);
                            return ResponseEntity.ok(rec);
                        })
                        .orElseGet(() -> ResponseEntity.notFound().build()),
                ValidationMessages.RESPONSE_NULL_MESSAGE);
    }

    @Operation(
            summary = "Record execution result",
            description = "Records the result of executing a recommendation.")
    @ApiResponse(responseCode = "200", description = "Execution result recorded")
    @ApiResponse(responseCode = "404", description = "Recommendation not found or not executing")
    @PostMapping("/{id}/execute/result")
    public ResponseEntity<SONRecommendation> recordResult(
            @Parameter(description = "Recommendation ID")
            @PathVariable String id,
            @Valid @RequestBody ExecutionResultRequest request) {

        return Objects.requireNonNull(
                sonService.recordExecutionResult(id, request.success(), request.result())
                        .map(rec -> {
                            log.info("SON recommendation {} execution result: success={}",
                                    id, request.success());
                            return ResponseEntity.ok(rec);
                        })
                        .orElseGet(() -> ResponseEntity.notFound().build()),
                ValidationMessages.RESPONSE_NULL_MESSAGE);
    }

    // ========================================================================
    // Statistics Endpoints
    // ========================================================================

    @Operation(
            summary = "Get SON statistics",
            description = "Returns overall statistics about SON recommendations.")
    @ApiResponse(responseCode = "200", description = "Statistics retrieved")
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        return ResponseEntity.ok(sonService.getStats());
    }

    @Operation(
            summary = "Get SON statistics for station",
            description = "Returns SON statistics for a specific station.")
    @ApiResponse(responseCode = "200", description = "Statistics retrieved")
    @GetMapping("/stats/station/{stationId}")
    public ResponseEntity<Map<String, Object>> getStatsForStation(
            @Parameter(description = "Station ID")
            @PathVariable Long stationId) {
        return ResponseEntity.ok(sonService.getStatsForStation(stationId));
    }

    // ========================================================================
    // Create Endpoint (typically called by AI service)
    // ========================================================================

    @Operation(
            summary = "Create recommendation",
            description = "Creates a new SON recommendation. Typically called by AI service.")
    @ApiResponse(responseCode = "200", description = "Recommendation created")
    @PostMapping
    public ResponseEntity<SONRecommendation> create(
            @Valid @RequestBody CreateRecommendationRequest request) {

        SONRecommendation recommendation = new SONRecommendation(
                request.stationId(),
                request.functionType(),
                request.actionType()
        );
        recommendation.setActionValue(request.actionValue());
        recommendation.setDescription(request.description());
        recommendation.setExpectedImprovement(request.expectedImprovement());
        recommendation.setConfidence(request.confidence());
        recommendation.setAutoExecutable(Boolean.TRUE.equals(request.autoExecutable()));
        recommendation.setApprovalRequired(!Boolean.FALSE.equals(request.approvalRequired()));
        recommendation.setRollbackAction(request.rollbackAction());

        SONRecommendation created = sonService.create(recommendation);
        log.info("Created SON recommendation {} for station {}", created.getId(), created.getStationId());

        return ResponseEntity.ok(created);
    }

    // ========================================================================
    // Helper Methods
    // ========================================================================

    private String getUsername(@Nullable UserDetails user) {
        return Objects.requireNonNull(
                Optional.ofNullable(user)
                        .map(UserDetails::getUsername)
                        .orElse(Roles.ANONYMOUS_USER));
    }

    // ========================================================================
    // Request DTOs
    // ========================================================================

    public record RejectRequest(
            @NotBlank(message = "Rejection reason is required")
            String reason
    ) {}

    public record RollbackRequest(
            @NotBlank(message = "Rollback reason is required")
            String reason
    ) {}

    public record ExecutionResultRequest(
            @NotNull(message = "Success flag is required")
            Boolean success,
            String result
    ) {}

    public record CreateRecommendationRequest(
            @NotNull(message = "Station ID is required")
            Long stationId,

            @NotNull(message = "Function type is required")
            SONFunction functionType,

            @NotBlank(message = "Action type is required")
            String actionType,

            String actionValue,
            String description,
            Double expectedImprovement,
            Double confidence,
            Boolean autoExecutable,
            Boolean approvalRequired,
            String rollbackAction
    ) {}
}
