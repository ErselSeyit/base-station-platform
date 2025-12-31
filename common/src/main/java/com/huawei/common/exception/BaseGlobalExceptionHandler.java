package com.huawei.common.exception;

import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;

/**
 * Base exception handler providing consistent error handling across all services.
 *
 * <p>Microservices should extend this class and add @RestControllerAdvice annotation.
 * This ensures consistent error response format and handling patterns platform-wide.
 *
 * <p>Features:
 * - Unique error ID generation for tracking
 * - Proper HTTP status codes
 * - No internal details exposed to clients
 * - Structured logging with error IDs
 * - Validation error handling
 *
 * <p>Example usage:
 * <pre>
 * {@code @RestControllerAdvice}
 * public class ServiceExceptionHandler extends BaseGlobalExceptionHandler {
 *     // Add service-specific exception handlers here
 * }
 * </pre>
 */
public abstract class BaseGlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(BaseGlobalExceptionHandler.class);

    /**
     * Handles IllegalArgumentException (400 Bad Request).
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(
            IllegalArgumentException e,
            WebRequest request) {
        String errorId = generateErrorId();
        log.warn("Illegal argument: {} [ErrorId: {}]", e.getMessage(), errorId);

        return ResponseEntity
                .badRequest()
                .body(new ErrorResponse(
                        "Invalid request",
                        e.getMessage(),
                        errorId,
                        extractPath(request)));
    }

    /**
     * Handles validation failures (400 Bad Request).
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(
            MethodArgumentNotValidException e,
            WebRequest request) {
        String errorId = generateErrorId();

        Map<String, String> errors = e.getBindingResult()
                .getFieldErrors()
                .stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        error -> error.getDefaultMessage() != null
                                ? error.getDefaultMessage()
                                : "Invalid value",
                        (existing, replacement) -> existing + "; " + replacement
                ));

        log.warn("Validation failed: {} [ErrorId: {}]", errors, errorId);

        return ResponseEntity
                .badRequest()
                .body(new ErrorResponse(
                        "Validation failed",
                        errors.toString(),
                        errorId,
                        extractPath(request)));
    }

    /**
     * Handles all unhandled exceptions (500 Internal Server Error).
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(
            Exception e,
            WebRequest request) {
        String errorId = generateErrorId();
        log.error("Unexpected error [ErrorId: {}]", errorId, e);

        // Don't expose internal error details to client
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse(
                        "An unexpected error occurred",
                        "Please contact support with error ID: " + errorId,
                        errorId,
                        extractPath(request)));
    }

    /**
     * Generates a unique 8-character error ID for tracking.
     */
    protected String generateErrorId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * Extracts the request path from WebRequest.
     */
    protected String extractPath(WebRequest request) {
        return request.getDescription(false).replace("uri=", "");
    }
}
