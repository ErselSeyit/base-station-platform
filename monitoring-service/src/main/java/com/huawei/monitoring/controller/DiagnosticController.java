package com.huawei.monitoring.controller;

import com.huawei.monitoring.model.DiagnosticSession;
import com.huawei.monitoring.model.DiagnosticStatus;
import com.huawei.monitoring.model.LearnedPattern;
import com.huawei.monitoring.service.DiagnosticSessionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.springframework.lang.Nullable;

/**
 * REST controller for AI diagnostic sessions and feedback.
 * Supports the AI learning system by capturing operator feedback on solutions.
 */
@RestController
@RequestMapping("/api/v1/diagnostics")
@PreAuthorize("isAuthenticated()")
public class DiagnosticController {

    private static final Logger log = LoggerFactory.getLogger(DiagnosticController.class);
    private static final String RESPONSE_NULL_MESSAGE = "Response cannot be null";

    private final DiagnosticSessionService sessionService;

    public DiagnosticController(DiagnosticSessionService sessionService) {
        this.sessionService = sessionService;
    }

    /**
     * Get all diagnostic sessions.
     */
    @GetMapping
    public ResponseEntity<List<DiagnosticSession>> getAllSessions() {
        List<DiagnosticSession> sessions = sessionService.getAllSessions();
        return ResponseEntity.ok(sessions);
    }

    /**
     * Get all diagnostic sessions pending confirmation.
     */
    @GetMapping("/pending")
    public ResponseEntity<List<DiagnosticSession>> getPendingConfirmation() {
        List<DiagnosticSession> sessions = sessionService.getPendingConfirmation();
        return ResponseEntity.ok(sessions);
    }

    /**
     * Get pending confirmations for a specific station.
     */
    @GetMapping("/pending/station/{stationId}")
    public ResponseEntity<List<DiagnosticSession>> getPendingForStation(@PathVariable Long stationId) {
        List<DiagnosticSession> sessions = sessionService.getPendingConfirmationForStation(stationId);
        return ResponseEntity.ok(sessions);
    }

    /**
     * Get a specific diagnostic session.
     */
    @GetMapping("/{sessionId}")
    public ResponseEntity<DiagnosticSession> getSession(@PathVariable String sessionId) {
        return Objects.requireNonNull(
                sessionService.getSession(sessionId)
                        .map(ResponseEntity::ok)
                        .orElseGet(() -> ResponseEntity.notFound().build()),
                RESPONSE_NULL_MESSAGE);
    }

    /**
     * Get all sessions for a station.
     */
    @GetMapping("/station/{stationId}")
    public ResponseEntity<List<DiagnosticSession>> getSessionsForStation(@PathVariable Long stationId) {
        List<DiagnosticSession> sessions = sessionService.getSessionsForStation(stationId);
        return ResponseEntity.ok(sessions);
    }

    /**
     * Get sessions by status.
     */
    @GetMapping("/status/{status}")
    public ResponseEntity<List<DiagnosticSession>> getSessionsByStatus(@PathVariable DiagnosticStatus status) {
        List<DiagnosticSession> sessions = sessionService.getSessionsByStatus(status);
        return ResponseEntity.ok(sessions);
    }

    /**
     * Mark a session as having its solution applied.
     */
    @PostMapping("/{sessionId}/apply")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public ResponseEntity<DiagnosticSession> markApplied(@PathVariable String sessionId) {
        return Objects.requireNonNull(
                sessionService.markApplied(sessionId)
                        .map(session -> {
                            log.info("Session {} marked as applied", sessionId);
                            return ResponseEntity.ok(session);
                        })
                        .orElseGet(() -> ResponseEntity.notFound().build()),
                RESPONSE_NULL_MESSAGE);
    }

    /**
     * Submit feedback for a diagnostic session.
     * This is the core learning mechanism - operator confirms if solution worked.
     */
    private static final String ANONYMOUS_USER = "anonymous";

    @PostMapping("/{sessionId}/feedback")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public ResponseEntity<DiagnosticSession> submitFeedback(
            @PathVariable String sessionId,
            @Valid @RequestBody FeedbackRequest request,
            @AuthenticationPrincipal @Nullable UserDetails user) {

        // Use Optional pattern instead of ternary null check
        String username = Objects.requireNonNull(
                Optional.ofNullable(user)
                        .map(UserDetails::getUsername)
                        .orElse(ANONYMOUS_USER));

        return Objects.requireNonNull(
                sessionService.submitFeedback(
                                sessionId,
                                request.wasEffective(),
                                request.rating(),
                                request.operatorNotes(),
                                request.actualOutcome(),
                                username)
                        .map(session -> {
                            log.info("Feedback submitted for session {} by {}: effective={}, rating={}",
                                    sessionId, username, request.wasEffective(), request.rating());
                            return ResponseEntity.ok(session);
                        })
                        .orElseGet(() -> ResponseEntity.notFound().build()),
                RESPONSE_NULL_MESSAGE);
    }

    /**
     * Get learning statistics.
     */
    @GetMapping("/learning/stats")
    public ResponseEntity<Map<String, Object>> getLearningStats() {
        Map<String, Object> stats = sessionService.getLearningStats();
        return ResponseEntity.ok(stats);
    }

    /**
     * Get all learned patterns.
     */
    @GetMapping("/learning/patterns")
    public ResponseEntity<List<LearnedPattern>> getLearnedPatterns() {
        List<LearnedPattern> patterns = sessionService.getAllPatterns();
        return ResponseEntity.ok(patterns);
    }

    /**
     * Get a specific learned pattern.
     */
    @GetMapping("/learning/patterns/{problemCode}")
    public ResponseEntity<LearnedPattern> getLearnedPattern(@PathVariable String problemCode) {
        return Objects.requireNonNull(
                sessionService.getLearnedPattern(problemCode)
                        .map(ResponseEntity::ok)
                        .orElseGet(() -> ResponseEntity.notFound().build()),
                RESPONSE_NULL_MESSAGE);
    }

    /**
     * Receive command execution result from base-station-service.
     * This automatically updates the learning engine based on whether the command succeeded.
     */
    @PostMapping("/{sessionId}/command-result")
    public ResponseEntity<Map<String, Object>> receiveCommandResult(
            @PathVariable String sessionId,
            @Valid @RequestBody CommandResultNotification notification) {

        log.info("Received command result for session {}: success={}",
                sessionId, notification.success());

        // Auto-submit feedback based on command success
        return Objects.requireNonNull(
                sessionService.submitFeedback(
                                sessionId,
                                notification.success(),
                                notification.success() ? 5 : 1,  // Auto-rate based on success
                                "Auto-feedback from command execution",
                                notification.success() ? "Command executed successfully" : "Command failed",
                                "system-auto")
                        .map(session -> {
                            log.info("Auto-feedback recorded for session {} based on command result", sessionId);
                            Map<String, Object> response = new java.util.HashMap<>();
                            response.put("status", "recorded");
                            response.put("sessionId", sessionId);
                            response.put("wasEffective", notification.success());
                            return ResponseEntity.ok(response);
                        })
                        .orElseGet(() -> {
                            Map<String, Object> response = new java.util.HashMap<>();
                            response.put("status", "session_not_found");
                            response.put("sessionId", sessionId);
                            return ResponseEntity.ok(response);
                        }),
                RESPONSE_NULL_MESSAGE);
    }

    /**
     * Request object for feedback submission.
     */
    public record FeedbackRequest(
            @NotNull(message = "wasEffective is required")
            Boolean wasEffective,

            @Min(value = 1, message = "Rating must be between 1 and 5")
            @Max(value = 5, message = "Rating must be between 1 and 5")
            @Nullable Integer rating,

            @Nullable String operatorNotes,

            @Nullable String actualOutcome
    ) {}

    /**
     * Notification from base-station-service about command execution result.
     */
    public record CommandResultNotification(
            @NotNull String commandId,
            @NotNull String diagnosticSessionId,
            String problemCode,
            @NotNull Boolean success,
            Integer returnCode
    ) {}
}
