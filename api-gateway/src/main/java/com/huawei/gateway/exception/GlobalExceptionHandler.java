package com.huawei.gateway.exception;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.web.WebProperties;
import org.springframework.boot.autoconfigure.web.reactive.error.AbstractErrorWebExceptionHandler;
import org.springframework.boot.web.reactive.error.ErrorAttributes;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.server.RequestPredicates;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.lang.Nullable;
import org.springframework.web.server.ResponseStatusException;

import static com.huawei.common.constants.JsonResponseKeys.KEY_MESSAGE;

import com.huawei.common.util.RequestUtils;

import reactor.core.publisher.Mono;

/**
 * Global exception handler for API Gateway.
 *
 * <p>Handles all exceptions in the gateway and returns consistent error responses
 * without exposing internal implementation details to clients.
 *
 * <p>This is a reactive exception handler for Spring Cloud Gateway.
 */
@Component
@Order(-2) // Higher precedence than DefaultErrorWebExceptionHandler
public class GlobalExceptionHandler extends AbstractErrorWebExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    public GlobalExceptionHandler(
            ErrorAttributes errorAttributes,
            WebProperties.Resources resources,
            ApplicationContext applicationContext,
            ServerCodecConfigurer serverCodecConfigurer) {
        super(errorAttributes, resources, applicationContext);
        setMessageWriters(serverCodecConfigurer.getWriters());
        setMessageReaders(serverCodecConfigurer.getReaders());
    }

    @Override
    protected RouterFunction<ServerResponse> getRoutingFunction(@Nullable ErrorAttributes errorAttributes) {
        return RouterFunctions.route(RequestPredicates.all(), this::renderErrorResponse);
    }

    @SuppressWarnings("null") // HttpStatus, MediaType.APPLICATION_JSON, and errorResponse are guaranteed non-null
    private Mono<ServerResponse> renderErrorResponse(ServerRequest request) {
        Throwable error = getError(request);

        String errorId = generateErrorId();
        HttpStatus status = determineHttpStatus(error);
        Map<String, Object> errorResponse = createErrorResponse(error, errorId, status);

        logError(error, errorId, status);

        return ServerResponse.status(status)
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(errorResponse));
    }

    private Map<String, Object> createErrorResponse(Throwable error, String errorId, HttpStatus status) {
        Map<String, Object> response = new HashMap<>();
        response.put("errorId", errorId);
        response.put("status", status.value());
        response.put("error", status.getReasonPhrase());

        if (error instanceof ResponseStatusException rse) {
            response.put(KEY_MESSAGE, Objects.requireNonNullElse(rse.getReason(), "Gateway error"));
        } else if (error instanceof IllegalArgumentException) {
            response.put(KEY_MESSAGE, error.getMessage());
        } else {
            // Don't expose internal error details to client
            response.put(KEY_MESSAGE, "An error occurred while processing your request");
            response.put("details", "Please contact support with error ID: " + errorId);
        }

        return response;
    }

    private HttpStatus determineHttpStatus(Throwable error) {
        if (error instanceof ResponseStatusException rse) {
            return HttpStatus.valueOf(rse.getStatusCode().value());
        } else if (error instanceof IllegalArgumentException) {
            return HttpStatus.BAD_REQUEST;
        } else if (error.getCause() instanceof java.net.ConnectException) {
            return HttpStatus.SERVICE_UNAVAILABLE;
        } else if (error.getMessage() != null && error.getMessage().contains("503")) {
            return HttpStatus.SERVICE_UNAVAILABLE;
        } else if (error.getMessage() != null && error.getMessage().contains("404")) {
            return HttpStatus.NOT_FOUND;
        } else {
            return HttpStatus.INTERNAL_SERVER_ERROR;
        }
    }

    private void logError(Throwable error, String errorId, HttpStatus status) {
        if (status.is5xxServerError()) {
            log.error("Gateway error [ErrorId: {}] Status: {}", errorId, status.value(), error);
        } else if (status.is4xxClientError()) {
            log.warn("Client error [ErrorId: {}] Status: {} - {}",
                    errorId, status.value(), error.getMessage());
        } else {
            log.info("Request handled with status [ErrorId: {}] Status: {}", errorId, status.value());
        }
    }

    private String generateErrorId() {
        return Objects.requireNonNull(UUID.randomUUID().toString().substring(0, RequestUtils.REQUEST_ID_LENGTH));
    }
}
