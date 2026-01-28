package com.huawei.monitoring.model;

import java.time.LocalDateTime;

/**
 * Embedded document representing operator feedback on a diagnostic solution.
 * This feedback is used by the AI learning system to improve future recommendations.
 */
public class SolutionFeedback {

    /**
     * Whether the solution effectively resolved the problem.
     */
    private Boolean wasEffective;

    /**
     * Rating from 1 (poor) to 5 (excellent).
     */
    private Integer rating;

    /**
     * Operator's notes about the solution or actual outcome.
     */
    private String operatorNotes;

    /**
     * What actually happened after applying the solution.
     */
    private String actualOutcome;

    /**
     * When the feedback was submitted.
     */
    private LocalDateTime confirmedAt;

    /**
     * Username of the operator who confirmed.
     */
    private String confirmedBy;

    public SolutionFeedback() {
    }

    public SolutionFeedback(Boolean wasEffective, Integer rating, String operatorNotes,
                            String actualOutcome, String confirmedBy) {
        this.wasEffective = wasEffective;
        this.rating = rating;
        this.operatorNotes = operatorNotes;
        this.actualOutcome = actualOutcome;
        this.confirmedBy = confirmedBy;
        this.confirmedAt = LocalDateTime.now();
    }

    public Boolean getWasEffective() {
        return wasEffective;
    }

    public void setWasEffective(Boolean wasEffective) {
        this.wasEffective = wasEffective;
    }

    public Integer getRating() {
        return rating;
    }

    public void setRating(Integer rating) {
        this.rating = rating;
    }

    public String getOperatorNotes() {
        return operatorNotes;
    }

    public void setOperatorNotes(String operatorNotes) {
        this.operatorNotes = operatorNotes;
    }

    public String getActualOutcome() {
        return actualOutcome;
    }

    public void setActualOutcome(String actualOutcome) {
        this.actualOutcome = actualOutcome;
    }

    public LocalDateTime getConfirmedAt() {
        return confirmedAt;
    }

    public void setConfirmedAt(LocalDateTime confirmedAt) {
        this.confirmedAt = confirmedAt;
    }

    public String getConfirmedBy() {
        return confirmedBy;
    }

    public void setConfirmedBy(String confirmedBy) {
        this.confirmedBy = confirmedBy;
    }
}
