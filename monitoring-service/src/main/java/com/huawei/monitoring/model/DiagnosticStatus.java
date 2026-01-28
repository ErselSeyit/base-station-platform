package com.huawei.monitoring.model;

/**
 * Status of a diagnostic session through its lifecycle.
 */
public enum DiagnosticStatus {
    /**
     * Problem detected but not yet diagnosed by AI.
     */
    DETECTED,

    /**
     * AI has generated a solution.
     */
    DIAGNOSED,

    /**
     * Solution has been applied/executed.
     */
    APPLIED,

    /**
     * Waiting for operator confirmation that solution worked.
     */
    PENDING_CONFIRMATION,

    /**
     * Solution was confirmed effective - problem resolved.
     */
    RESOLVED,

    /**
     * Solution did not work - problem not resolved.
     */
    FAILED
}
