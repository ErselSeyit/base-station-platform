package com.huawei.auth.exception;

import com.huawei.auth.dto.ErrorResponse;
import com.huawei.auth.dto.ErrorResponse.ErrorCode;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Global exception handler for consistent error responses across the auth
 * service.
 *
 * <p>
 * This handler catches all exceptions thrown by controllers and converts them
 * into standardized ErrorResponse objects with appropriate HTTP status codes.
 *
 * <p>
 * Benefits:
 * - Consistent error format across all endpoints
 * - Prevents stack traces from leaking to clients
 * - Centralized logging of errors
 * - Easy to maintain and extend
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

        private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

        // ========================================================================
        // Authentication & Authorization Errors
        // ========================================================================

        @ExceptionHandler(BadCredentialsException.class)
        public ResponseEntity<ErrorResponse> handleBadCredentials(
                        BadCredentialsException ex, HttpServletRequest request) {
                log.warn("Authentication failed for request to {}: {}", request.getRequestURI(), ex.getMessage());

                return ResponseEntity
                                .status(HttpStatus.UNAUTHORIZED)
                                .body(ErrorResponse.of(ErrorCode.AUTH_INVALID_CREDENTIALS, request.getRequestURI()));
        }

        @ExceptionHandler(LockedException.class)
        public ResponseEntity<ErrorResponse> handleLocked(
                        LockedException ex, HttpServletRequest request) {
                log.warn("Account locked for request to {}: {}", request.getRequestURI(), ex.getMessage());

                return ResponseEntity
                                .status(HttpStatus.FORBIDDEN)
                                .body(ErrorResponse.of(ErrorCode.AUTH_ACCOUNT_LOCKED, request.getRequestURI()));
        }

        @ExceptionHandler(AuthenticationException.class)
        public ResponseEntity<ErrorResponse> handleAuthentication(
                        AuthenticationException ex, HttpServletRequest request) {
                log.warn("Authentication error for request to {}: {}", request.getRequestURI(), ex.getMessage());

                return ResponseEntity
                                .status(HttpStatus.UNAUTHORIZED)
                                .body(ErrorResponse.of(ErrorCode.AUTH_TOKEN_INVALID, request.getRequestURI(),
                                                ex.getMessage()));
        }

        @ExceptionHandler(AccessDeniedException.class)
        public ResponseEntity<ErrorResponse> handleAccessDenied(
                        AccessDeniedException ex, HttpServletRequest request) {
                log.warn("Access denied for request to {}: {}", request.getRequestURI(), ex.getMessage());

                return ResponseEntity
                                .status(HttpStatus.FORBIDDEN)
                                .body(ErrorResponse.of(ErrorCode.AUTH_INSUFFICIENT_PERMISSIONS,
                                                request.getRequestURI()));
        }

        // ========================================================================
        // Validation Errors
        // ========================================================================

        @ExceptionHandler(MethodArgumentNotValidException.class)
        public ResponseEntity<ErrorResponse> handleValidation(
                        MethodArgumentNotValidException ex, HttpServletRequest request) {
                Map<String, String> fieldErrors = ex.getBindingResult()
                                .getFieldErrors()
                                .stream()
                                .collect(Collectors.toMap(
                                                FieldError::getField,
                                                error -> error.getDefaultMessage() != null ? error.getDefaultMessage()
                                                                : "Invalid value",
                                                (first, second) -> first // Keep first error if field has multiple
                                ));

                log.warn("Validation failed for request to {}: {}", request.getRequestURI(), fieldErrors);

                return ResponseEntity
                                .status(HttpStatus.BAD_REQUEST)
                                .body(ErrorResponse.builder()
                                                .code(ErrorCode.VAL_INVALID_INPUT.getCode())
                                                .message("Validation failed")
                                                .path(request.getRequestURI())
                                                .details(fieldErrors)
                                                .build());
        }

        @ExceptionHandler(ConstraintViolationException.class)
        public ResponseEntity<ErrorResponse> handleConstraintViolation(
                        ConstraintViolationException ex, HttpServletRequest request) {
                Map<String, String> violations = new HashMap<>();
                ex.getConstraintViolations().forEach(violation -> violations.put(violation.getPropertyPath().toString(),
                                violation.getMessage()));

                log.warn("Constraint violation for request to {}: {}", request.getRequestURI(), violations);

                return ResponseEntity
                                .status(HttpStatus.BAD_REQUEST)
                                .body(ErrorResponse.builder()
                                                .code(ErrorCode.VAL_INVALID_INPUT.getCode())
                                                .message("Constraint violation")
                                                .path(request.getRequestURI())
                                                .details(violations)
                                                .build());
        }

        @ExceptionHandler(MissingServletRequestParameterException.class)
        public ResponseEntity<ErrorResponse> handleMissingParam(
                        MissingServletRequestParameterException ex, HttpServletRequest request) {
                log.warn("Missing parameter for request to {}: {}", request.getRequestURI(), ex.getParameterName());

                return ResponseEntity
                                .status(HttpStatus.BAD_REQUEST)
                                .body(ErrorResponse.of(
                                                ErrorCode.VAL_MISSING_FIELD,
                                                request.getRequestURI(),
                                                "Missing required parameter: " + ex.getParameterName()));
        }

        @ExceptionHandler(org.springframework.web.bind.MissingRequestHeaderException.class)
        public ResponseEntity<ErrorResponse> handleMissingHeader(
                        org.springframework.web.bind.MissingRequestHeaderException ex, HttpServletRequest request) {
                log.warn("Missing header for request to {}: {}", request.getRequestURI(), ex.getHeaderName());

                return ResponseEntity
                                .status(HttpStatus.BAD_REQUEST)
                                .body(ErrorResponse.of(
                                                ErrorCode.VAL_MISSING_FIELD,
                                                request.getRequestURI(),
                                                "Missing required header: " + ex.getHeaderName()));
        }

        @ExceptionHandler(MethodArgumentTypeMismatchException.class)
        public ResponseEntity<ErrorResponse> handleTypeMismatch(
                        MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
                log.warn("Type mismatch for request to {}: parameter '{}' expected type {}",
                                request.getRequestURI(), ex.getName(), ex.getRequiredType());

                return ResponseEntity
                                .status(HttpStatus.BAD_REQUEST)
                                .body(ErrorResponse.of(
                                                ErrorCode.VAL_INVALID_FORMAT,
                                                request.getRequestURI(),
                                                "Invalid value for parameter: " + ex.getName()));
        }

        @ExceptionHandler(HttpMessageNotReadableException.class)
        public ResponseEntity<ErrorResponse> handleUnreadableMessage(
                        HttpMessageNotReadableException ex, HttpServletRequest request) {
                log.warn("Unreadable request body for request to {}", request.getRequestURI());

                return ResponseEntity
                                .status(HttpStatus.BAD_REQUEST)
                                .body(ErrorResponse.of(
                                                ErrorCode.VAL_INVALID_FORMAT,
                                                request.getRequestURI(),
                                                "Invalid request body format"));
        }

        // ========================================================================
        // Resource Errors
        // ========================================================================

        @ExceptionHandler(NoHandlerFoundException.class)
        public ResponseEntity<ErrorResponse> handleNotFound(
                        NoHandlerFoundException ex, HttpServletRequest request) {
                log.debug("No handler found for {} {}", ex.getHttpMethod(), ex.getRequestURL());

                return ResponseEntity
                                .status(HttpStatus.NOT_FOUND)
                                .body(ErrorResponse.of(ErrorCode.RES_NOT_FOUND, request.getRequestURI()));
        }

        @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
        public ResponseEntity<ErrorResponse> handleMethodNotSupported(
                        HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
                log.debug("Method {} not supported for {}", ex.getMethod(), request.getRequestURI());

                return ResponseEntity
                                .status(HttpStatus.METHOD_NOT_ALLOWED)
                                .body(ErrorResponse.of(
                                                ErrorCode.VAL_INVALID_INPUT,
                                                request.getRequestURI(),
                                                "HTTP method " + ex.getMethod() + " not supported"));
        }

        // ========================================================================
        // Catch-All for Unexpected Errors
        // ========================================================================

        @ExceptionHandler(Exception.class)
        public ResponseEntity<ErrorResponse> handleAllOther(
                        Exception ex, HttpServletRequest request) {
                // Log the full stack trace for debugging
                log.error("Unexpected error for request to {}: {}", request.getRequestURI(), ex.getMessage(), ex);

                // Return generic error to client (don't expose internal details)
                return ResponseEntity
                                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                                .body(ErrorResponse.of(ErrorCode.SRV_INTERNAL_ERROR, request.getRequestURI()));
        }
}
