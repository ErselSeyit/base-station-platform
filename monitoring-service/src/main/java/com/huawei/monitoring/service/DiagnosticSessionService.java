package com.huawei.monitoring.service;

import com.huawei.common.constants.DiagnosticConstants;
import com.huawei.common.dto.AlertEvent;
import com.huawei.common.dto.DiagnosticResponse;
import com.huawei.monitoring.model.AISolution;
import com.huawei.monitoring.model.DiagnosticSession;
import com.huawei.monitoring.model.DiagnosticStatus;
import com.huawei.monitoring.model.LearnedPattern;
import com.huawei.monitoring.model.SolutionFeedback;
import com.huawei.monitoring.repository.DiagnosticSessionRepository;
import com.huawei.monitoring.repository.LearnedPatternRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Transactional;

import static com.huawei.common.constants.ServiceNames.SYSTEM_ACTOR;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.springframework.scheduling.annotation.Scheduled;

/**
 * Service for managing diagnostic sessions and the AI learning system.
 * Tracks problem-solution pairs and learns from operator feedback.
 */
@Service
@SuppressWarnings("null") // Spring Data repositories and Optional operations guarantee non-null for present values
public class DiagnosticSessionService {

    private static final Logger log = LoggerFactory.getLogger(DiagnosticSessionService.class);

    private final DiagnosticSessionRepository sessionRepository;
    private final LearnedPatternRepository patternRepository;

    public DiagnosticSessionService(DiagnosticSessionRepository sessionRepository,
                                    LearnedPatternRepository patternRepository) {
        this.sessionRepository = sessionRepository;
        this.patternRepository = patternRepository;
    }

    private static final String DEFAULT_SEVERITY = "medium";
    private static final String DEFAULT_CATEGORY = "unknown";
    private static final String UNKNOWN_METRIC_KEY = "unknown_metric";

    /**
     * Creates a new diagnostic session from an alert event, or returns existing active session.
     * Uses pre-processing to prevent duplicate sessions for the same station+problemCode.
     *
     * @param alert the alert event (must not be null)
     * @param problemId the problem identifier (must not be null)
     * @return the created or existing session
     */
    @Transactional
    public DiagnosticSession createSession(AlertEvent alert, String problemId) {
        Objects.requireNonNull(alert, "AlertEvent must not be null");
        Objects.requireNonNull(problemId, "problemId must not be null");

        String problemCode = mapAlertToProblemCode(alert);
        Long stationId = alert.getStationId();

        // Pre-processing: Check for existing active session to prevent duplicates
        if (stationId != null) {
            List<DiagnosticSession> activeSessions = sessionRepository
                    .findActiveByStationIdAndProblemCode(stationId, problemCode);
            if (!activeSessions.isEmpty()) {
                DiagnosticSession existing = activeSessions.get(0);
                log.debug("Reusing existing active session {} for station {} problem {}",
                        existing.getId(), stationId, problemCode);
                // Update the metrics snapshot with latest values
                updateMetricsSnapshot(existing, alert);
                try {
                    return sessionRepository.save(existing);
                } catch (Exception e) {
                    // Concurrent modification - return existing session without saving metrics update
                    // This is safe because the session ID is still valid for diagnosis
                    log.debug("Concurrent save conflict for session {}, returning existing: {}",
                            existing.getId(), e.getMessage());
                    return existing;
                }
            }
        }

        // Use Optional to safely extract and transform nullable values
        String category = Optional.ofNullable(alert.getMetricType())
                .map(this::mapMetricTypeToCategory)
                .orElse(DEFAULT_CATEGORY);

        String severity = Optional.ofNullable(alert.getSeverity())
                .map(String::toLowerCase)
                .orElse(DEFAULT_SEVERITY);

        DiagnosticSession session = new DiagnosticSession(
                problemId,
                stationId,
                alert.getStationName(),
                category,
                severity,
                problemCode,
                alert.getMessage()
        );

        // Capture metrics snapshot - use safe key for null metric type
        updateMetricsSnapshot(session, alert);

        try {
            DiagnosticSession saved = sessionRepository.save(session);
            log.info("Created new diagnostic session {} for station {} problem {}",
                    problemId, stationId, problemCode);
            return saved;
        } catch (DuplicateKeyException e) {
            // Race condition: Another thread created a session for the same station+problemCode
            // This is caught by the unique partial index on active sessions
            log.debug("Race condition detected - another thread created session for station {} problem {}",
                    stationId, problemCode);
            // Return the existing session created by the other thread
            if (stationId != null) {
                List<DiagnosticSession> existing = sessionRepository
                        .findActiveByStationIdAndProblemCode(stationId, problemCode);
                if (!existing.isEmpty()) {
                    return Objects.requireNonNull(existing.get(0));
                }
            }
            // Fallback: try to find by problemId
            return Objects.requireNonNull(sessionRepository.findByProblemId(problemId).orElse(session));
        }
    }

    /**
     * Updates the metrics snapshot with values from an alert event.
     * Validates metric values to prevent storing null/invalid data.
     */
    private void updateMetricsSnapshot(DiagnosticSession session, AlertEvent alert) {
        Map<String, Object> metrics = session.getMetricsSnapshot();
        if (metrics == null) {
            metrics = new HashMap<>();
        }
        String metricKey = Objects.requireNonNullElse(alert.getMetricType(), UNKNOWN_METRIC_KEY);

        // Validate metric value - log warning if null and use placeholder
        Double metricValue = alert.getMetricValue();
        if (metricValue == null) {
            log.warn("Null metric value in alert for station {} (metricType={}, alertRule={}). " +
                     "Using -1.0 as placeholder.",
                     alert.getStationId(), alert.getMetricType(), alert.getAlertRuleId());
            metricValue = -1.0; // Placeholder indicating missing value
        }
        metrics.put(metricKey, metricValue);

        // Validate threshold as well
        Double threshold = alert.getThreshold();
        if (threshold != null) {
            metrics.put("threshold", threshold);
        } else {
            log.debug("No threshold in alert for station {} (metricType={})",
                     alert.getStationId(), alert.getMetricType());
        }

        log.debug("Updated metrics snapshot for session {}: {}={}, threshold={}",
                 session.getProblemId(), metricKey, metricValue, threshold);
        session.setMetricsSnapshot(metrics);
    }

    /**
     * Records an AI diagnosis for a session.
     * If confidence is high enough based on risk level, auto-applies the solution.
     *
     * Thread-safety: Uses optimistic locking (@Version) to prevent concurrent modifications.
     * Only processes sessions in DETECTED status to prevent double processing.
     *
     * @return the updated session, or empty if session not found or already processed
     */
    @Transactional
    public Optional<DiagnosticSession> recordDiagnosis(String problemId, DiagnosticResponse diagnosis) {
        Optional<DiagnosticSession> sessionOpt = sessionRepository.findByProblemId(problemId);
        if (sessionOpt.isEmpty()) {
            log.warn("No session found for problem ID: {}", problemId);
            return Objects.requireNonNull(Optional.empty());
        }

        DiagnosticSession session = sessionOpt.get();

        // Guard: Only process sessions in DETECTED status (prevents double processing)
        if (session.getStatus() != DiagnosticStatus.DETECTED) {
            log.debug("Skipping diagnosis for session {} - already in {} status (race condition avoided)",
                    problemId, session.getStatus());
            return Objects.requireNonNull(Optional.of(session));
        }

        AISolution solution = new AISolution(
                diagnosis.getAction(),
                diagnosis.getCommands(),
                diagnosis.getExpectedOutcome(),
                diagnosis.getRiskLevel(),
                diagnosis.getConfidence(),
                diagnosis.getReasoning()
        );

        // Check for learned pattern adjustments
        patternRepository.findByProblemCode(Objects.requireNonNull(session.getProblemCode()))
                .ifPresent(pattern -> {
                    Double adjustedConfidence = pattern.getAdjustedConfidence();
                    if (adjustedConfidence != null) {
                        solution.setConfidence(adjustedConfidence);
                        if (log.isInfoEnabled()) {
                            log.info("Adjusted confidence for {} from {} to {} based on {} historical cases",
                                    session.getProblemCode(),
                                    diagnosis.getConfidence(),
                                    adjustedConfidence,
                                    pattern.getResolvedCount() + pattern.getFailedCount());
                        }
                    }
                });

        session.markDiagnosed(solution);
        log.info("Marked session {} as DIAGNOSED with confidence={}", problemId, solution.getConfidence());

        // Check if solution qualifies for auto-confirmation (no operator approval needed)
        if (shouldAutoApply(solution)) {
            session.markApplied();
            session.setAutoApplied(true);
            autoResolveSolution(session, solution);
            log.info("AUTO-APPLIED solution for session {} (confidence={}, risk={})",
                    session.getId(), solution.getConfidence(), solution.getRiskLevel());
        }

        try {
            DiagnosticSession saved = sessionRepository.save(session);
            log.info("Saved diagnostic session {} with status={}", saved.getProblemId(), saved.getStatus());
            return Objects.requireNonNull(Optional.of(saved));
        } catch (OptimisticLockingFailureException e) {
            // Another thread modified the session - this is expected in concurrent scenarios
            log.info("Optimistic locking conflict for session {} - another thread processed it first", problemId);
            // Return the current state from database
            return sessionRepository.findByProblemId(problemId);
        }
    }

    /**
     * Determines if a solution should be auto-applied based on confidence and risk level.
     * HIGH risk actions always require operator confirmation.
     * MEDIUM risk requires 95%+ confidence.
     * LOW risk requires 90%+ confidence.
     *
     * @see DiagnosticConstants for threshold values
     */
    private boolean shouldAutoApply(@Nullable AISolution solution) {
        if (solution == null || solution.getConfidence() == null) {
            return false;
        }

        double confidence = solution.getConfidence();
        String riskLevel = solution.getRiskLevel() != null
                ? solution.getRiskLevel().toUpperCase()
                : "MEDIUM";

        // HIGH risk always requires confirmation regardless of confidence
        if ("HIGH".equals(riskLevel)) {
            return false;
        }

        // LOW risk can auto-apply at lower confidence threshold
        if ("LOW".equals(riskLevel)) {
            return confidence >= DiagnosticConstants.CONFIDENCE_AUTO_APPLY_LOW_RISK;
        }

        // MEDIUM (or unknown) risk requires higher confidence
        return confidence >= DiagnosticConstants.CONFIDENCE_AUTO_APPLY_MEDIUM_RISK;
    }

    /**
     * Auto-resolves a solution that was automatically applied.
     * Creates system feedback indicating automatic resolution.
     */
    private void autoResolveSolution(DiagnosticSession session, AISolution solution) {
        SolutionFeedback feedback = new SolutionFeedback(
                true, // wasEffective - assumed true for auto-applied high-confidence solutions
                5,    // rating - auto-applied solutions get max rating initially
                "Auto-applied due to high confidence (" +
                        String.format("%.1f%%", solution.getConfidence() * 100) + ")",
                "Solution auto-applied based on confidence threshold",
                SYSTEM_ACTOR
        );
        session.markResolved(feedback);

        // Update learning pattern with auto-apply success
        updateLearningPattern(session, true, 5);

        log.info("Auto-resolved session {} for problem code {}",
                session.getId(), session.getProblemCode());
    }

    /**
     * Marks a session as having its solution applied.
     * @return the updated session, or empty if session not found
     */
    @Transactional
    public Optional<DiagnosticSession> markApplied(String sessionId) {
        return Objects.requireNonNull(sessionRepository.findById(sessionId)
                .map(session -> {
                    session.markApplied();
                    session.markPendingConfirmation();
                    return sessionRepository.save(session);
                }));
    }

    /**
     * Submits feedback for a diagnostic session and updates learning patterns.
     * @return the updated session, or empty if session not found
     */
    @Transactional
    public Optional<DiagnosticSession> submitFeedback(String sessionId, boolean wasEffective,
                                                       @Nullable Integer rating, @Nullable String operatorNotes,
                                                       @Nullable String actualOutcome, String confirmedBy) {
        Optional<DiagnosticSession> sessionOpt = sessionRepository.findById(sessionId);
        if (sessionOpt.isEmpty()) {
            log.warn("No session found for ID: {}", sessionId);
            return Objects.requireNonNull(Optional.empty());
        }

        DiagnosticSession session = sessionOpt.get();
        SolutionFeedback feedback = new SolutionFeedback(
                wasEffective,
                rating,
                operatorNotes,
                actualOutcome,
                confirmedBy
        );

        if (wasEffective) {
            session.markResolved(feedback);
        } else {
            session.markFailed(feedback);
        }

        DiagnosticSession savedSession = sessionRepository.save(session);

        // Update learning patterns
        updateLearningPattern(session, wasEffective, rating);

        log.info("Feedback recorded for session {}: effective={}, rating={}",
                sessionId, wasEffective, rating);

        return Objects.requireNonNull(Optional.of(savedSession));
    }

    private static final int MAX_LEARNING_RETRIES = 3;
    private static final int MIN_SAMPLES_FOR_RELIABLE_CONFIDENCE = 5;

    /**
     * Updates the learning pattern based on feedback.
     * Uses optimistic locking with exponential backoff retry for concurrent updates.
     *
     * Optimization: Skip learning updates if AI solution is missing or invalid.
     */
    private void updateLearningPattern(DiagnosticSession session, boolean wasEffective, @Nullable Integer rating) {
        AISolution solution = session.getAiSolution();
        if (solution == null || solution.getAction() == null || solution.getAction().isBlank()) {
            log.debug("Skipping learning update - no valid AI solution for session {}", session.getProblemId());
            return;
        }

        String problemCode = Objects.requireNonNull(session.getProblemCode());
        String category = Objects.requireNonNull(session.getCategory());

        for (int attempt = 1; attempt <= MAX_LEARNING_RETRIES; attempt++) {
            if (tryUpdatePattern(session, wasEffective, rating, problemCode, category, attempt)) {
                return;
            }
            // Exponential backoff between retries
            if (attempt < MAX_LEARNING_RETRIES) {
                try {
                    Thread.sleep((long) Math.pow(2, attempt) * 10); // 20ms, 40ms
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.warn("Learning update interrupted for pattern {}", problemCode);
                    return;
                }
            }
        }
        log.warn("Learning update failed for pattern {} after {} retries", problemCode, MAX_LEARNING_RETRIES);
    }

    /**
     * Attempts to update a pattern, returning true on success.
     * Handles both OptimisticLockingFailureException and DuplicateKeyException
     * which can occur during concurrent pattern updates.
     */
    private boolean tryUpdatePattern(DiagnosticSession session, boolean wasEffective,
                                     @Nullable Integer rating, String problemCode,
                                     String category, int attempt) {
        try {
            LearnedPattern pattern = getOrCreatePattern(problemCode, category);
            applyFeedbackToPattern(pattern, session.getAiSolution(), wasEffective, rating, problemCode);
            patternRepository.save(pattern);
            return true;
        } catch (OptimisticLockingFailureException | DuplicateKeyException e) {
            // Both exceptions indicate concurrent modification - retry with fresh data
            if (attempt < MAX_LEARNING_RETRIES) {
                log.debug("Concurrent conflict for pattern {} ({}), retry {}/{}",
                        problemCode, e.getClass().getSimpleName(), attempt, MAX_LEARNING_RETRIES);
            }
            return false;
        }
    }

    /**
     * Applies feedback to a learning pattern with optimized confidence calculation.
     *
     * Learning optimization:
     * - Tracks success/failure for specific actions
     * - Requires minimum samples before adjusting confidence significantly
     * - Logs learning progress for monitoring
     */
    private void applyFeedbackToPattern(LearnedPattern pattern, AISolution solution,
                                        boolean wasEffective, @Nullable Integer rating,
                                        String problemCode) {
        int previousTotal = pattern.getResolvedCount() + pattern.getFailedCount();

        if (wasEffective) {
            pattern.recordSuccess(solution, rating);
            if (log.isInfoEnabled()) {
                log.info("LEARNING [{}]: SUCCESS recorded (total: {} resolved, {} failed, rate: {}%)",
                        problemCode, pattern.getResolvedCount(), pattern.getFailedCount(),
                        String.format("%.1f", pattern.getSuccessRate()));
            }
        } else {
            pattern.recordFailure(solution);
            if (log.isInfoEnabled()) {
                log.info("LEARNING [{}]: FAILURE recorded (total: {} resolved, {} failed, rate: {}%)",
                        problemCode, pattern.getResolvedCount(), pattern.getFailedCount(),
                        String.format("%.1f", pattern.getSuccessRate()));
            }
        }

        // Log confidence adjustment milestone when reaching minimum samples
        int newTotal = pattern.getResolvedCount() + pattern.getFailedCount();
        if (log.isInfoEnabled()
                && previousTotal < MIN_SAMPLES_FOR_RELIABLE_CONFIDENCE
                && newTotal >= MIN_SAMPLES_FOR_RELIABLE_CONFIDENCE) {
            log.info("LEARNING [{}]: Reached {} samples - confidence now statistically reliable (adjusted: {})",
                    problemCode, MIN_SAMPLES_FOR_RELIABLE_CONFIDENCE,
                    String.format("%.2f", pattern.getAdjustedConfidence()));
        }
    }

    /**
     * Gets an existing pattern or creates a new one with pre-processing to prevent duplicates.
     *
     * Algorithm:
     * 1. First check: Query for existing pattern
     * 2. If not found: Create and save new pattern
     * 3. On DuplicateKeyException: Re-fetch (another thread created it)
     * 4. Verify: Final check to ensure pattern exists
     *
     * This pre-processing approach minimizes duplicate creation attempts by checking
     * existence before save, while still handling race conditions gracefully.
     */
    private LearnedPattern getOrCreatePattern(String problemCode, String category) {
        // Pre-processing: Check if pattern already exists BEFORE attempting creation
        Optional<LearnedPattern> existing = patternRepository.findByProblemCode(problemCode);
        if (existing.isPresent()) {
            log.debug("Found existing learning pattern for {}", problemCode);
            return existing.get();
        }

        // Pattern doesn't exist, create it
        log.info("Creating new learning pattern for {} (category={})", problemCode, category);
        LearnedPattern newPattern = new LearnedPattern(problemCode, category);

        try {
            LearnedPattern saved = patternRepository.save(newPattern);
            log.info("Successfully created learning pattern for {}", problemCode);
            return Objects.requireNonNull(saved);
        } catch (DuplicateKeyException e) {
            // Race condition: Another thread created the pattern between our check and save
            log.debug("Concurrent creation detected for pattern {}, fetching existing", problemCode);
            return patternRepository.findByProblemCode(problemCode)
                    .orElseThrow(() -> new IllegalStateException(
                            "Pattern " + problemCode + " should exist after DuplicateKeyException"));
        }
    }

    /**
     * Gets all diagnostic sessions ordered by creation date (most recent first).
     */
    @Transactional(readOnly = true)
    public List<DiagnosticSession> getAllSessions() {
        return sessionRepository.findAllByOrderByCreatedAtDesc();
    }

    /**
     * Gets all sessions pending confirmation.
     */
    @Transactional(readOnly = true)
    public List<DiagnosticSession> getPendingConfirmation() {
        return sessionRepository.findPendingConfirmation();
    }

    /**
     * Gets all sessions pending confirmation for a station.
     */
    @Transactional(readOnly = true)
    public List<DiagnosticSession> getPendingConfirmationForStation(Long stationId) {
        return sessionRepository.findByStationIdAndStatusOrderByCreatedAtDesc(
                stationId, DiagnosticStatus.PENDING_CONFIRMATION);
    }

    /**
     * Marks a session as failed due to a diagnosis error (e.g., AI service unavailable).
     * This prevents sessions from being stuck in DETECTED status forever.
     *
     * @param problemId the problem identifier
     * @param reason the failure reason
     * @return the updated session, or empty if not found
     */
    @Transactional
    public Optional<DiagnosticSession> markSessionError(String problemId, String reason) {
        Optional<DiagnosticSession> sessionOpt = sessionRepository.findByProblemId(problemId);
        if (sessionOpt.isEmpty()) {
            log.warn("Cannot mark session error - no session found for problem ID: {}", problemId);
            return Optional.empty();
        }

        DiagnosticSession session = sessionOpt.get();

        // Only mark as failed if still in DETECTED status (not yet processed)
        if (session.getStatus() != DiagnosticStatus.DETECTED) {
            log.debug("Skipping error marking for session {} - already in {} status",
                    problemId, session.getStatus());
            return Optional.of(session);
        }

        // Create a failed feedback entry with the error reason
        SolutionFeedback errorFeedback = new SolutionFeedback(
                false,  // wasEffective
                0,      // rating
                reason, // operatorNotes
                "Diagnosis service error", // actualOutcome
                "SYSTEM" // confirmedBy
        );

        session.markFailed(errorFeedback);
        DiagnosticSession saved = sessionRepository.save(session);

        log.info("Marked session {} as FAILED due to diagnosis error: {}", problemId, reason);
        return Optional.of(saved);
    }

    /**
     * Gets a session by ID.
     */
    @Transactional(readOnly = true)
    public Optional<DiagnosticSession> getSession(String sessionId) {
        return sessionRepository.findById(sessionId);
    }

    /**
     * Gets a session by problem ID.
     */
    @Transactional(readOnly = true)
    public Optional<DiagnosticSession> getSessionByProblemId(String problemId) {
        return sessionRepository.findByProblemId(problemId);
    }

    /**
     * Gets all sessions for a station.
     */
    @Transactional(readOnly = true)
    public List<DiagnosticSession> getSessionsForStation(Long stationId) {
        return sessionRepository.findByStationIdOrderByCreatedAtDesc(stationId);
    }

    /**
     * Gets sessions by status.
     */
    @Transactional(readOnly = true)
    public List<DiagnosticSession> getSessionsByStatus(DiagnosticStatus status) {
        return sessionRepository.findByStatusOrderByCreatedAtDesc(status);
    }

    /**
     * Gets learning statistics.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getLearningStats() {
        Map<String, Object> stats = new HashMap<>();

        long resolved = sessionRepository.countByStatus(DiagnosticStatus.RESOLVED);
        long failed = sessionRepository.countByStatus(DiagnosticStatus.FAILED);
        long pending = sessionRepository.countByStatus(DiagnosticStatus.PENDING_CONFIRMATION);
        long autoApplied = sessionRepository.countByAutoApplied(true);
        long total = resolved + failed;

        stats.put("totalFeedback", total);
        stats.put("resolved", resolved);
        stats.put("failed", failed);
        stats.put("pendingConfirmation", pending);
        stats.put("autoApplied", autoApplied);
        stats.put("successRate", total > 0 ? (double) resolved / total * 100 : 0.0);

        // Get pattern statistics
        List<LearnedPattern> patterns = patternRepository.findAllOrderByTotalCases();
        stats.put("learnedPatterns", patterns.size());
        stats.put("topPatterns", patterns.stream()
                .limit(5)
                .map(p -> Map.of(
                        "problemCode", p.getProblemCode(),
                        "successRate", p.getSuccessRate(),
                        "totalCases", p.getResolvedCount() + p.getFailedCount(),
                        "adjustedConfidence", p.getAdjustedConfidence()
                ))
                .toList());

        return stats;
    }

    /**
     * Gets the learned pattern for a problem code.
     */
    @Transactional(readOnly = true)
    public Optional<LearnedPattern> getLearnedPattern(String problemCode) {
        return patternRepository.findByProblemCode(problemCode);
    }

    /**
     * Gets all learned patterns.
     */
    @Transactional(readOnly = true)
    public List<LearnedPattern> getAllPatterns() {
        return patternRepository.findAllOrderByTotalCases();
    }

    /**
     * Maps metric type to problem category.
     * Contract: metricType is guaranteed non-null by caller (using Optional).
     */
    private String mapMetricTypeToCategory(String metricType) {
        // Caller guarantees non-null via Optional.map() - no defensive check needed
        return switch (metricType.toUpperCase()) {
            case "CPU_USAGE", "MEMORY_USAGE", "FAN_SPEED" -> "hardware";
            case "TEMPERATURE", "POWER_CONSUMPTION" -> "power";
            case "SIGNAL_STRENGTH", "DATA_THROUGHPUT", "CONNECTION_COUNT" -> "network";
            default -> "software";
        };
    }

    private static final String UNKNOWN_PROBLEM_CODE = "UNKNOWN";

    /**
     * Maps alert to a problem code that matches AI diagnostic service codes.
     * Returns empty collections pattern: always returns a valid code, never null.
     */
    private String mapAlertToProblemCode(AlertEvent alert) {
        return Objects.requireNonNull(Optional.ofNullable(alert.getMetricType())
                .map(String::toUpperCase)
                .map(this::metricTypeToProblemCode)
                .orElse(UNKNOWN_PROBLEM_CODE));
    }

    /**
     * Maps a metric type to its corresponding problem code.
     * Contract: metricType is guaranteed non-null and uppercase by caller.
     */
    private String metricTypeToProblemCode(String metricType) {
        return switch (metricType) {
            case "CPU_USAGE", "TEMPERATURE" -> "CPU_OVERHEAT";
            case "MEMORY_USAGE" -> "MEMORY_PRESSURE";
            case "SIGNAL_STRENGTH" -> "SIGNAL_DEGRADATION";
            case "POWER_CONSUMPTION" -> "HIGH_POWER_CONSUMPTION";
            default -> metricType + "_ISSUE";
        };
    }

    // ========================================
    // Stale Session Cleanup
    // ========================================

    /** Sessions stuck in DETECTED for longer than this are considered stale. */
    private static final long STALE_SESSION_THRESHOLD_MINUTES = 5;

    /**
     * Scheduled task to clean up stale diagnostic sessions.
     * Runs every 2 minutes to find sessions stuck in DETECTED status and marks them as FAILED.
     * This prevents sessions from being stuck indefinitely due to AI service failures.
     */
    @Scheduled(fixedRate = 120_000) // Run every 2 minutes
    @Transactional
    public void cleanupStaleSessions() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(STALE_SESSION_THRESHOLD_MINUTES);

        List<DiagnosticSession> staleSessions = sessionRepository
                .findByStatusOrderByCreatedAtDesc(DiagnosticStatus.DETECTED)
                .stream()
                .filter(session -> {
                    LocalDateTime createdAt = session.getCreatedAt();
                    return createdAt != null && createdAt.isBefore(threshold);
                })
                .toList();

        if (staleSessions.isEmpty()) {
            return;
        }

        log.info("Found {} stale diagnostic sessions (stuck in DETECTED for >{}min), marking as FAILED",
                staleSessions.size(), STALE_SESSION_THRESHOLD_MINUTES);

        for (DiagnosticSession session : staleSessions) {
            try {
                SolutionFeedback errorFeedback = new SolutionFeedback(
                        false,  // wasEffective
                        0,      // rating
                        "Session timed out - stuck in DETECTED status", // operatorNotes
                        "AI diagnosis did not complete within timeout", // actualOutcome
                        "SYSTEM_CLEANUP" // confirmedBy
                );
                session.markFailed(errorFeedback);
                sessionRepository.save(session);
                log.debug("Marked stale session {} as FAILED (created at {})",
                        session.getProblemId(), session.getCreatedAt());
            } catch (Exception e) {
                log.warn("Failed to mark stale session {} as FAILED: {}",
                        session.getProblemId(), e.getMessage());
            }
        }

        log.info("Cleaned up {} stale diagnostic sessions", staleSessions.size());
    }
}
