package com.huawei.monitoring.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents learned patterns from diagnostic feedback.
 * Used by the AI to improve future recommendations.
 *
 * Thread-safety: Uses @Version for optimistic locking to prevent
 * concurrent update race conditions during AI learning.
 */
@Document(collection = "learned_patterns")
public class LearnedPattern {

    @Id
    private String id;

    /**
     * Version field for optimistic locking.
     * Prevents concurrent updates from overwriting each other during learning.
     */
    @Version
    private Long version;

    /**
     * Problem code this pattern applies to.
     */
    @Indexed(unique = true)
    private String problemCode;

    /**
     * Problem category.
     */
    private String category;

    /**
     * Solutions that have proven successful.
     */
    private List<SolutionRecord> successfulSolutions = new ArrayList<>();

    /**
     * Solutions that have failed.
     */
    private List<SolutionRecord> failedSolutions = new ArrayList<>();

    /**
     * Adjusted confidence based on historical success rate.
     * Starts at 0.85 (rule-based default) and adjusts based on feedback.
     */
    private Double adjustedConfidence = 0.85;

    /**
     * Total number of resolved cases.
     */
    private Integer resolvedCount = 0;

    /**
     * Total number of failed cases.
     */
    private Integer failedCount = 0;

    /**
     * When this pattern was last updated.
     */
    private LocalDateTime lastUpdated;

    public LearnedPattern() {
        this.lastUpdated = LocalDateTime.now();
    }

    public LearnedPattern(String problemCode, String category) {
        this();
        this.problemCode = problemCode;
        this.category = category;
    }

    /**
     * Embedded record for solution performance.
     */
    public static class SolutionRecord {
        private String action;
        private List<String> commands;
        private Integer count = 0;
        private Double totalRating = 0.0;

        public SolutionRecord() {
        }

        public SolutionRecord(String action, List<String> commands) {
            this.action = action;
            this.commands = commands;
            this.count = 1;
        }

        public String getAction() {
            return action;
        }

        public void setAction(String action) {
            this.action = action;
        }

        public List<String> getCommands() {
            return commands;
        }

        public void setCommands(List<String> commands) {
            this.commands = commands;
        }

        public Integer getCount() {
            return count;
        }

        public void setCount(Integer count) {
            this.count = count;
        }

        public Double getTotalRating() {
            return totalRating;
        }

        public void setTotalRating(Double totalRating) {
            this.totalRating = totalRating;
        }

        public Double getAverageRating() {
            return count > 0 ? totalRating / count : 0.0;
        }

        public void incrementCount() {
            this.count++;
        }

        public void addRating(Integer rating) {
            this.totalRating += rating;
        }
    }

    // Getters and Setters

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getProblemCode() {
        return problemCode;
    }

    public void setProblemCode(String problemCode) {
        this.problemCode = problemCode;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public List<SolutionRecord> getSuccessfulSolutions() {
        return successfulSolutions;
    }

    public void setSuccessfulSolutions(List<SolutionRecord> successfulSolutions) {
        this.successfulSolutions = successfulSolutions;
    }

    public List<SolutionRecord> getFailedSolutions() {
        return failedSolutions;
    }

    public void setFailedSolutions(List<SolutionRecord> failedSolutions) {
        this.failedSolutions = failedSolutions;
    }

    public Double getAdjustedConfidence() {
        return adjustedConfidence;
    }

    public void setAdjustedConfidence(Double adjustedConfidence) {
        this.adjustedConfidence = adjustedConfidence;
    }

    public Integer getResolvedCount() {
        return resolvedCount;
    }

    public void setResolvedCount(Integer resolvedCount) {
        this.resolvedCount = resolvedCount;
    }

    public Integer getFailedCount() {
        return failedCount;
    }

    public void setFailedCount(Integer failedCount) {
        this.failedCount = failedCount;
    }

    public LocalDateTime getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(LocalDateTime lastUpdated) {
        this.lastUpdated = lastUpdated;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    /**
     * Records a successful solution application.
     */
    public void recordSuccess(AISolution solution, Integer rating) {
        this.resolvedCount++;
        this.lastUpdated = LocalDateTime.now();

        // Find or create solution record
        SolutionRecord solutionRecord = findOrCreateRecord(successfulSolutions, solution);
        solutionRecord.incrementCount();
        if (rating != null) {
            solutionRecord.addRating(rating);
        }

        recalculateConfidence();
    }

    /**
     * Records a failed solution application.
     */
    public void recordFailure(AISolution solution) {
        this.failedCount++;
        this.lastUpdated = LocalDateTime.now();

        SolutionRecord solutionRecord = findOrCreateRecord(failedSolutions, solution);
        solutionRecord.incrementCount();

        recalculateConfidence();
    }

    private SolutionRecord findOrCreateRecord(List<SolutionRecord> records, AISolution solution) {
        for (SolutionRecord existing : records) {
            if (existing.getAction().equals(solution.getAction())) {
                return existing;
            }
        }
        SolutionRecord newRecord = new SolutionRecord(solution.getAction(), solution.getCommands());
        records.add(newRecord);
        return newRecord;
    }

    /**
     * Recalculates confidence based on success rate.
     */
    private void recalculateConfidence() {
        int total = resolvedCount + failedCount;
        if (total > 0) {
            double successRate = (double) resolvedCount / total;
            // Weighted average: 30% base confidence + 70% success rate
            this.adjustedConfidence = 0.3 * 0.85 + 0.7 * successRate;
        }
    }

    /**
     * Gets the success rate as a percentage.
     */
    public Double getSuccessRate() {
        int total = resolvedCount + failedCount;
        return total > 0 ? (double) resolvedCount / total * 100 : 0.0;
    }
}
