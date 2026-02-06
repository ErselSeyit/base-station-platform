package com.huawei.monitoring.exception;

import java.io.IOException;

import org.apache.catalina.connector.ClientAbortException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.huawei.common.exception.BaseGlobalExceptionHandler;

/**
 * Global exception handler for monitoring service.
 *
 * <p>Extends the base exception handler from common module to provide
 * consistent error handling across all services in the platform.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends BaseGlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Handles client abort exceptions (broken pipe).
     * This occurs when the client disconnects before the response is fully sent.
     * Logged at DEBUG level since this is usually benign (client timeout, navigation away, etc).
     */
    @ExceptionHandler(ClientAbortException.class)
    public void handleClientAbort(ClientAbortException ex) {
        log.debug("Client disconnected before response completed: {}", ex.getMessage());
        // No response needed - client is gone
    }

    /**
     * Handles IO exceptions that may wrap client disconnection errors.
     */
    @ExceptionHandler(IOException.class)
    public void handleIOException(IOException ex) {
        if (ex.getMessage() != null && ex.getMessage().contains("Broken pipe")) {
            log.debug("Client disconnected (broken pipe): {}", ex.getMessage());
        } else {
            log.warn("IO error during request processing: {}", ex.getMessage());
        }
        // No response needed for connection errors
    }
}
