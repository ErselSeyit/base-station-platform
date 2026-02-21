package com.huawei.monitoring.service;

import com.huawei.monitoring.model.AISolution;
import com.huawei.monitoring.model.DiagnosticSession;
import com.huawei.monitoring.model.DiagnosticStatus;
import com.huawei.monitoring.model.LearnedPattern;
import com.huawei.monitoring.repository.DiagnosticSessionRepository;
import com.huawei.monitoring.repository.LearnedPatternRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

/**
 * Service for managing learned patterns from diagnostic feedback.
 * Tracks problem-solution effectiveness and adjusts confidence based on historical data.
 */
@Service
@SuppressWarnings("null") // Spring Data repositories guarantee non-null for present values
public class LearningPatternService {

    private static final Logger log = LoggerFactory.getLogger(LearningPatternService.class);

    private static final int MAX_LEARNING_RETRIES = 3;
    private static final int MIN_SAMPLES_FOR_RELIABLE_CONFIDENCE = 5;
    private static final String FIELD_PROBLEM_CODE = "problemCode";

    private final LearnedPatternRepository patternRepository;
    private final DiagnosticSessionRepository sessionRepository;
    private final MongoTemplate mongoTemplate;

    public LearningPatternService(LearnedPatternRepository patternRepository,
                                  DiagnosticSessionRepository sessionRepository,
                                  MongoTemplate mongoTemplate) {
        this.patternRepository = patternRepository;
        this.sessionRepository = sessionRepository;
        this.mongoTemplate = mongoTemplate;
    }

    /**
     * Updates the learning pattern based on feedback.
     * Uses optimistic locking with exponential backoff retry for concurrent updates.
     *
     * Optimization: Skip learning updates if AI solution is missing or invalid.
     */
    public void updateLearningPattern(DiagnosticSession session, boolean wasEffective, @Nullable Integer rating) {
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
     * Gets an existing pattern or creates a new one using atomic MongoDB upsert.
     *
     * Uses findAndModify with upsert=true for atomicity — no race condition
     * possible between check and insert. If the document exists, it is returned
     * unchanged. If not, it is created in a single operation.
     */
    private LearnedPattern getOrCreatePattern(String problemCode, String category) {
        Query query = new Query(Criteria.where(FIELD_PROBLEM_CODE).is(problemCode));
        Update update = new Update()
                .setOnInsert(FIELD_PROBLEM_CODE, problemCode)
                .setOnInsert("category", category)
                .setOnInsert("adjustedConfidence", 0.85)
                .setOnInsert("resolvedCount", 0)
                .setOnInsert("failedCount", 0)
                .setOnInsert("lastUpdated", LocalDateTime.now())
                .setOnInsert("successfulSolutions", List.of())
                .setOnInsert("failedSolutions", List.of());

        FindAndModifyOptions options = FindAndModifyOptions.options()
                .upsert(true)
                .returnNew(true);

        LearnedPattern pattern = mongoTemplate.findAndModify(query, update, options, LearnedPattern.class);
        return Objects.requireNonNull(pattern, "Upsert should always return a document");
    }

    /**
     * Adjusts solution confidence based on learned patterns.
     *
     * @param solution the AI solution to adjust
     * @param problemCode the problem code to look up patterns for
     * @return true if confidence was adjusted, false otherwise
     */
    public boolean adjustConfidenceFromPattern(AISolution solution, String problemCode) {
        Optional<LearnedPattern> patternOpt;
        try {
            patternOpt = patternRepository.findByProblemCode(problemCode);
        } catch (org.springframework.dao.IncorrectResultSizeDataAccessException e) {
            log.warn("Multiple LearnedPattern documents for '{}' — skipping confidence adjustment", problemCode);
            return false;
        }
        if (patternOpt.isEmpty()) {
            return false;
        }

        LearnedPattern pattern = patternOpt.get();
        Double adjustedConfidence = pattern.getAdjustedConfidence();
        if (adjustedConfidence != null && solution.getConfidence() != null) {
            double originalConfidence = solution.getConfidence();
            solution.setConfidence(adjustedConfidence);
            if (log.isInfoEnabled()) {
                log.info("Adjusted confidence for {} from {} to {} based on {} historical cases",
                        problemCode, originalConfidence, adjustedConfidence,
                        pattern.getResolvedCount() + pattern.getFailedCount());
            }
            return true;
        }
        return false;
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

        // Get pattern statistics - show all patterns (no limit)
        List<LearnedPattern> patterns = patternRepository.findAllOrderByTotalCases();
        stats.put("learnedPatterns", patterns.size());
        stats.put("topPatterns", patterns.stream()
                .map(p -> Map.of(
                        FIELD_PROBLEM_CODE, p.getProblemCode(),
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
     * Gets all learned patterns ordered by total cases.
     */
    @Transactional(readOnly = true)
    public List<LearnedPattern> getAllPatterns() {
        return patternRepository.findAllOrderByTotalCases();
    }
}
