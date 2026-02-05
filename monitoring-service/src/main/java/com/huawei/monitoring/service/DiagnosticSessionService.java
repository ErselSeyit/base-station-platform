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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Service for managing diagnostic sessions and the AI learning system.
 * Tracks problem-solution pairs and learns from operator feedback.
 */
@Service
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
                return sessionRepository.save(existing);
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
     */
    private void updateMetricsSnapshot(DiagnosticSession session, AlertEvent alert) {
        Map<String, Object> metrics = session.getMetricsSnapshot();
        if (metrics == null) {
            metrics = new HashMap<>();
        }
        String metricKey = Objects.requireNonNullElse(alert.getMetricType(), UNKNOWN_METRIC_KEY);
        metrics.put(metricKey, alert.getMetricValue());
        metrics.put("threshold", alert.getThreshold());
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

    /**
     * Updates the learning pattern based on feedback.
     * Uses optimistic locking with retry to handle concurrent updates.
     */
    private void updateLearningPattern(DiagnosticSession session, boolean wasEffective, @Nullable Integer rating) {
        if (session.getAiSolution() == null) {
            return;
        }

        String problemCode = Objects.requireNonNull(session.getProblemCode());
        String category = Objects.requireNonNull(session.getCategory());

        for (int attempt = 1; attempt <= MAX_LEARNING_RETRIES; attempt++) {
            if (tryUpdatePattern(session, wasEffective, rating, problemCode, category, attempt)) {
                return;
            }
        }
    }

    /**
     * Attempts to update a pattern, returning true on success.
     */
    private boolean tryUpdatePattern(DiagnosticSession session, boolean wasEffective,
                                     @Nullable Integer rating, String problemCode,
                                     String category, int attempt) {
        try {
            LearnedPattern pattern = getOrCreatePattern(problemCode, category);
            applyFeedbackToPattern(pattern, session.getAiSolution(), wasEffective, rating, problemCode);
            patternRepository.save(pattern);
            return true;
        } catch (OptimisticLockingFailureException e) {
            logRetryAttempt(problemCode, attempt);
            return false;
        }
    }

    private void applyFeedbackToPattern(LearnedPattern pattern, AISolution solution,
                                        boolean wasEffective, @Nullable Integer rating,
                                        String problemCode) {
        if (wasEffective) {
            pattern.recordSuccess(solution, rating);
            if (log.isInfoEnabled()) {
                log.info("Recorded SUCCESS for pattern {}: successRate={}%",
                        problemCode, String.format("%.1f", pattern.getSuccessRate()));
            }
        } else {
            pattern.recordFailure(solution);
            if (log.isInfoEnabled()) {
                log.info("Recorded FAILURE for pattern {}: successRate={}%",
                        problemCode, String.format("%.1f", pattern.getSuccessRate()));
            }
        }
    }

    private void logRetryAttempt(String problemCode, int attempt) {
        if (attempt < MAX_LEARNING_RETRIES) {
            log.debug("Optimistic locking conflict for pattern {}, retrying (attempt {}/{})",
                    problemCode, attempt, MAX_LEARNING_RETRIES);
        } else {
            log.warn("Failed to update pattern {} after {} attempts due to concurrent modifications",
                    problemCode, MAX_LEARNING_RETRIES);
        }
    }

    /**
     * Gets an existing pattern or creates a new one, handling race conditions.
     * If two threads try to create the same pattern simultaneously, the unique
     * index on problemCode will cause one to fail with DuplicateKeyException.
     * We catch this and retry the find to get the pattern created by the other thread.
     */
    private LearnedPattern getOrCreatePattern(String problemCode, String category) {
        return Objects.requireNonNull(patternRepository.findByProblemCode(problemCode)
                .orElseGet(() -> {
                    try {
                        LearnedPattern newPattern = new LearnedPattern(problemCode, category);
                        return Objects.requireNonNull(patternRepository.save(newPattern));
                    } catch (DuplicateKeyException e) {
                        // Another thread created the pattern first - fetch it
                        log.debug("Pattern {} was created by another thread, fetching existing", problemCode);
                        return patternRepository.findByProblemCode(problemCode)
                                .orElseThrow(() -> new IllegalStateException(
                                        "Pattern " + problemCode + " should exist after DuplicateKeyException"));
                    }
                }));
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
}
