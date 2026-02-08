package com.huawei.basestation.dto;

import org.springframework.lang.Nullable;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Response DTO for bulk station import.
 */
public record BulkImportResponse(
        int totalRequested,
        int successCount,
        int failedCount,
        int skippedCount,
        int updatedCount,
        boolean dryRun,
        List<ImportResult> results,
        LocalDateTime timestamp
) {
    /**
     * Result for individual station import.
     */
    public record ImportResult(
            String stationName,
            Status status,
            String message,
            @Nullable Long stationId
    ) {
        public enum Status {
            CREATED,
            UPDATED,
            SKIPPED,
            FAILED
        }
    }

    @SuppressWarnings("null") // List.of() and LocalDateTime.now() are guaranteed non-null
    public static BulkImportResponse empty(boolean dryRun) {
        return new BulkImportResponse(0, 0, 0, 0, 0, dryRun, List.of(), LocalDateTime.now());
    }
}
