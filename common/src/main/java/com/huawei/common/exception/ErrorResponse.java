package com.huawei.common.exception;

import java.time.LocalDateTime;

/**
 * Standard error response format used across all services.
 * Provides consistent error responses to clients.
 */
public class ErrorResponse {

    private final String message;
    private final String details;
    private final String errorId;
    private final LocalDateTime timestamp;
    private final String path;

    public ErrorResponse(String message, String details, String errorId) {
        this(message, details, errorId, null);
    }

    public ErrorResponse(String message, String details, String errorId, String path) {
        this.message = message;
        this.details = details;
        this.errorId = errorId;
        this.timestamp = LocalDateTime.now();
        this.path = path;
    }

    public String getMessage() {
        return message;
    }

    public String getDetails() {
        return details;
    }

    public String getErrorId() {
        return errorId;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public String getPath() {
        return path;
    }
}
