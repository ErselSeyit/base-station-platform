package com.huawei.common.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryContext;
import org.springframework.retry.RetryListener;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;

import java.net.ConnectException;
import java.sql.SQLException;
import java.sql.SQLTransientConnectionException;
import java.util.Map;

/**
 * Shared retry configuration for transient failure handling.
 *
 * Provides retry templates for database operations and external service calls.
 * Services can import this configuration to get consistent retry behavior.
 */
@Configuration
@EnableRetry
public class RetryConfig {

    private static final Logger log = LoggerFactory.getLogger(RetryConfig.class);

    /**
     * Default retry template for database operations.
     * Retries up to 3 times with exponential backoff.
     */
    @Bean
    public RetryTemplate databaseRetryTemplate() {
        RetryTemplate template = new RetryTemplate();

        // Exponential backoff: 1s, 2s, 4s
        ExponentialBackOffPolicy backOffPolicy = new ExponentialBackOffPolicy();
        backOffPolicy.setInitialInterval(1000);
        backOffPolicy.setMultiplier(2.0);
        backOffPolicy.setMaxInterval(10000);
        template.setBackOffPolicy(backOffPolicy);

        // Retry only for transient database exceptions
        Map<Class<? extends Throwable>, Boolean> retryableExceptions = Map.of(
                SQLTransientConnectionException.class, true,
                ConnectException.class, true,
                SQLException.class, false  // Don't retry non-transient SQL errors
        );
        SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy(3, retryableExceptions, true);
        template.setRetryPolicy(retryPolicy);

        // Add logging listener
        template.registerListener(new RetryListener() {
            @Override
            public <T, E extends Throwable> void onError(
                    RetryContext context, RetryCallback<T, E> callback, Throwable throwable) {
                log.warn("Database operation failed, attempt {}: {}",
                        context.getRetryCount(), throwable.getMessage());
            }
        });

        return template;
    }

    /**
     * Retry template for external HTTP service calls.
     * More aggressive backoff for external services.
     */
    @Bean
    public RetryTemplate externalServiceRetryTemplate() {
        RetryTemplate template = new RetryTemplate();

        // Exponential backoff: 500ms, 1.5s, 4.5s
        ExponentialBackOffPolicy backOffPolicy = new ExponentialBackOffPolicy();
        backOffPolicy.setInitialInterval(500);
        backOffPolicy.setMultiplier(3.0);
        backOffPolicy.setMaxInterval(15000);
        template.setBackOffPolicy(backOffPolicy);

        // Retry on connection errors
        Map<Class<? extends Throwable>, Boolean> retryableExceptions = Map.of(
                ConnectException.class, true,
                java.net.SocketTimeoutException.class, true,
                java.io.IOException.class, true
        );
        SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy(3, retryableExceptions, true);
        template.setRetryPolicy(retryPolicy);

        template.registerListener(new RetryListener() {
            @Override
            public <T, E extends Throwable> void onError(
                    RetryContext context, RetryCallback<T, E> callback, Throwable throwable) {
                log.warn("External service call failed, attempt {}: {}",
                        context.getRetryCount(), throwable.getMessage());
            }
        });

        return template;
    }
}
