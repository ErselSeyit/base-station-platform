package com.huawei.basestation.exception;

import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.huawei.common.exception.BaseGlobalExceptionHandler;

/**
 * Global exception handler for base station service.
 *
 * <p>Extends the base exception handler from common module to provide
 * consistent error handling across all services in the platform.
 *
 * <p>Additional service-specific exception handlers can be added here if needed.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends BaseGlobalExceptionHandler {
    // All common exception handling is inherited from BaseGlobalExceptionHandler
    // Add base-station-specific exception handlers here if needed
}
