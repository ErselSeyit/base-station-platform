package com.huawei.auth.dto;

import java.time.Instant;

/**
 * Standardized error response DTO for consistent API error handling.
 *
 * <p>This class provides a uniform structure for all error responses,
 * making it easier for clients to parse and handle errors consistently.
 */
public class ErrorResponse {

    private final String code;
    private final String message;
    private final String path;
    private final Instant timestamp;
    private final Object details;

    private ErrorResponse(Builder builder) {
        this.code = builder.code;
        this.message = builder.message;
        this.path = builder.path;
        this.timestamp = builder.timestamp != null ? builder.timestamp : Instant.now();
        this.details = builder.details;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public String getPath() {
        return path;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public Object getDetails() {
        return details;
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Convenience factory method for common error codes.
     */
    public static ErrorResponse of(ErrorCode errorCode, String path) {
        return builder()
                .code(errorCode.getCode())
                .message(errorCode.getMessage())
                .path(path)
                .build();
    }

    public static ErrorResponse of(ErrorCode errorCode, String path, String customMessage) {
        return builder()
                .code(errorCode.getCode())
                .message(customMessage)
                .path(path)
                .build();
    }

    public static class Builder {
        private String code;
        private String message;
        private String path;
        private Instant timestamp;
        private Object details;

        public Builder code(String code) {
            this.code = code;
            return this;
        }

        public Builder message(String message) {
            this.message = message;
            return this;
        }

        public Builder path(String path) {
            this.path = path;
            return this;
        }

        public Builder timestamp(Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder details(Object details) {
            this.details = details;
            return this;
        }

        public ErrorResponse build() {
            return new ErrorResponse(this);
        }
    }

    /**
     * Common error codes for the authentication service.
     */
    public enum ErrorCode {
        // Authentication errors (AUTH_xxx)
        AUTH_INVALID_CREDENTIALS("AUTH_001", "Invalid username or password"),
        AUTH_ACCOUNT_LOCKED("AUTH_002", "Account is locked due to too many failed attempts"),
        AUTH_TOKEN_EXPIRED("AUTH_003", "Authentication token has expired"),
        AUTH_TOKEN_INVALID("AUTH_004", "Invalid authentication token"),
        AUTH_INSUFFICIENT_PERMISSIONS("AUTH_005", "Insufficient permissions for this operation"),

        // Validation errors (VAL_xxx)
        VAL_INVALID_INPUT("VAL_001", "Invalid input data"),
        VAL_MISSING_FIELD("VAL_002", "Required field is missing"),
        VAL_INVALID_FORMAT("VAL_003", "Invalid data format"),

        // Resource errors (RES_xxx)
        RES_NOT_FOUND("RES_001", "Requested resource not found"),
        RES_ALREADY_EXISTS("RES_002", "Resource already exists"),
        RES_CONFLICT("RES_003", "Resource conflict"),

        // Rate limiting (RATE_xxx)
        RATE_LIMIT_EXCEEDED("RATE_001", "Too many requests, please try again later"),

        // Server errors (SRV_xxx)
        SRV_INTERNAL_ERROR("SRV_001", "An internal server error occurred"),
        SRV_SERVICE_UNAVAILABLE("SRV_002", "Service is temporarily unavailable"),
        SRV_DATABASE_ERROR("SRV_003", "Database operation failed");

        private final String code;
        private final String message;

        ErrorCode(String code, String message) {
            this.code = code;
            this.message = message;
        }

        public String getCode() {
            return code;
        }

        public String getMessage() {
            return message;
        }
    }
}
