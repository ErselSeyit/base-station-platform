package com.huawei.common.dto;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class DiagnosticResolutionEventTest {

    @Test
    void successFactorySetsWasEffectiveTrue() {
        DiagnosticResolutionEvent event = DiagnosticResolutionEvent.success(
                "session-1", "prob-1", 42L, "CPU_OVERHEAT", "admin"
        );

        assertThat(event.wasEffective()).isTrue();
    }

    @Test
    void failureFactorySetsWasEffectiveFalse() {
        DiagnosticResolutionEvent event = DiagnosticResolutionEvent.failure(
                "session-1", "prob-1", 42L, "CPU_OVERHEAT", "admin"
        );

        assertThat(event.wasEffective()).isFalse();
    }

    @Test
    void successFactorySetsResolvedAtToApproximatelyNow() {
        LocalDateTime before = LocalDateTime.now().minusSeconds(1);
        DiagnosticResolutionEvent event = DiagnosticResolutionEvent.success(
                "session-1", "prob-1", 42L, "CPU_OVERHEAT", "admin"
        );
        LocalDateTime after = LocalDateTime.now().plusSeconds(1);

        assertThat(event.resolvedAt()).isAfter(before);
        assertThat(event.resolvedAt()).isBefore(after);
    }

    @Test
    void failureFactorySetsResolvedAtToApproximatelyNow() {
        LocalDateTime before = LocalDateTime.now().minusSeconds(1);
        DiagnosticResolutionEvent event = DiagnosticResolutionEvent.failure(
                "session-1", "prob-1", 42L, "CPU_OVERHEAT", "admin"
        );
        LocalDateTime after = LocalDateTime.now().plusSeconds(1);

        assertThat(event.resolvedAt()).isAfter(before);
        assertThat(event.resolvedAt()).isBefore(after);
    }

    @Test
    void recordAccessorsWorkCorrectly() {
        DiagnosticResolutionEvent event = DiagnosticResolutionEvent.success(
                "session-abc", "prob-xyz", 99L, "MEMORY_PRESSURE", "operator"
        );

        assertThat(event.sessionId()).isEqualTo("session-abc");
        assertThat(event.problemId()).isEqualTo("prob-xyz");
        assertThat(event.stationId()).isEqualTo(99L);
        assertThat(event.problemCode()).isEqualTo("MEMORY_PRESSURE");
        assertThat(event.resolvedBy()).isEqualTo("operator");
    }

    @Test
    void constructorSetsAllFieldsDirectly() {
        LocalDateTime resolvedAt = LocalDateTime.of(2025, 6, 15, 12, 0, 0);

        DiagnosticResolutionEvent event = new DiagnosticResolutionEvent(
                "s1", "p1", 1L, "CODE", true, resolvedAt, "user1"
        );

        assertThat(event.sessionId()).isEqualTo("s1");
        assertThat(event.problemId()).isEqualTo("p1");
        assertThat(event.stationId()).isEqualTo(1L);
        assertThat(event.problemCode()).isEqualTo("CODE");
        assertThat(event.wasEffective()).isTrue();
        assertThat(event.resolvedAt()).isEqualTo(resolvedAt);
        assertThat(event.resolvedBy()).isEqualTo("user1");
    }

    @Test
    void successAndFailureDifferOnlyInWasEffective() {
        DiagnosticResolutionEvent success = DiagnosticResolutionEvent.success(
                "s1", "p1", 1L, "CODE", "admin"
        );
        DiagnosticResolutionEvent failure = DiagnosticResolutionEvent.failure(
                "s1", "p1", 1L, "CODE", "admin"
        );

        assertThat(success.sessionId()).isEqualTo(failure.sessionId());
        assertThat(success.problemId()).isEqualTo(failure.problemId());
        assertThat(success.stationId()).isEqualTo(failure.stationId());
        assertThat(success.problemCode()).isEqualTo(failure.problemCode());
        assertThat(success.resolvedBy()).isEqualTo(failure.resolvedBy());
        assertThat(success.wasEffective()).isNotEqualTo(failure.wasEffective());
    }
}
