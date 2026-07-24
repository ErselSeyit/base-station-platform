package io.github.erselseyit.basestation.common.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Event DTO sent when a diagnostic session is resolved.
 * Used to notify notification-service to mark related alerts as resolved.
 */
public record DiagnosticResolutionEvent(
    @JsonProperty("sessionId")
    String sessionId,

    @JsonProperty("problemId")
    String problemId,

    @JsonProperty("stationId")
    Long stationId,

    @JsonProperty("problemCode")
    String problemCode,

    @JsonProperty("wasEffective")
    boolean wasEffective,

    @JsonProperty("resolvedAt")
    LocalDateTime resolvedAt,

    @JsonProperty("resolvedBy")
    String resolvedBy
) {
    /**
     * Creates a resolution event for a successful resolution.
     */
    public static DiagnosticResolutionEvent success(
            String sessionId,
            String problemId,
            Long stationId,
            String problemCode,
            String resolvedBy) {
        return new DiagnosticResolutionEvent(
                sessionId,
                problemId,
                stationId,
                problemCode,
                true,
                Objects.requireNonNull(LocalDateTime.now()),
                resolvedBy
        );
    }

    /**
     * Creates a resolution event for a failed resolution.
     */
    public static DiagnosticResolutionEvent failure(
            String sessionId,
            String problemId,
            Long stationId,
            String problemCode,
            String resolvedBy) {
        return new DiagnosticResolutionEvent(
                sessionId,
                problemId,
                stationId,
                problemCode,
                false,
                Objects.requireNonNull(LocalDateTime.now()),
                resolvedBy
        );
    }
}
