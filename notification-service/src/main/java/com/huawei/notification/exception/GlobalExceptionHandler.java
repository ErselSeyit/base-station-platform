package com.huawei.notification.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import com.huawei.common.exception.BaseGlobalExceptionHandler;
import com.huawei.common.exception.ErrorResponse;

/**
 * Global exception handler for notification service.
 *
 * <p>Extends the base exception handler from common module to provide
 * consistent error handling across all services in the platform.
 *
 * <p>Includes notification-specific exception handlers (ResourceNotFoundException).
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends BaseGlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Handles ResourceNotFoundException (404 Not Found).
     * This is specific to notification service.
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFound(
            ResourceNotFoundException e,
            WebRequest request) {
        String errorId = generateErrorId();
        log.warn("Resource not found: {} [ErrorId: {}]", e.getMessage(), errorId);

        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse(
                        "Resource not found",
                        e.getMessage(),
                        errorId,
                        extractPath(request)));
    }
}
