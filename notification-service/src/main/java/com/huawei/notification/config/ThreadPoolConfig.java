package com.huawei.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

import jakarta.annotation.PostConstruct;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * Externalized configuration for thread pool settings.
 * Single source of truth for notification executor configuration.
 *
 * <p>Configure in application.yml:
 * <pre>
 * notification:
 *   executor:
 *     core-size: 5
 *     max-size: 10
 *     queue-capacity: 100
 *     thread-name-prefix: notification-
 *     await-termination-seconds: 30
 * </pre>
 */
@Configuration
@ConfigurationProperties(prefix = "notification.executor")
@Validated
public class ThreadPoolConfig {

    @Min(value = 1, message = "Core size must be at least 1")
    private int coreSize = 5;

    @Min(value = 1, message = "Max size must be at least 1")
    private int maxSize = 10;

    @Min(value = 0, message = "Queue capacity cannot be negative")
    private int queueCapacity = 100;

    @NotBlank(message = "Thread name prefix is required")
    private String threadNamePrefix = "notification-";

    @Min(value = 0, message = "Await termination seconds cannot be negative")
    private int awaitTerminationSeconds = 30;

    private boolean waitForTasksToCompleteOnShutdown = true;

    @PostConstruct
    public void validate() {
        if (coreSize > maxSize) {
            throw new IllegalStateException(
                    String.format("Thread pool coreSize (%d) cannot exceed maxSize (%d)", coreSize, maxSize));
        }
    }

    public int getCoreSize() {
        return coreSize;
    }

    public void setCoreSize(int coreSize) {
        this.coreSize = coreSize;
    }

    public int getMaxSize() {
        return maxSize;
    }

    public void setMaxSize(int maxSize) {
        this.maxSize = maxSize;
    }

    public int getQueueCapacity() {
        return queueCapacity;
    }

    public void setQueueCapacity(int queueCapacity) {
        this.queueCapacity = queueCapacity;
    }

    public String getThreadNamePrefix() {
        return threadNamePrefix;
    }

    public void setThreadNamePrefix(String threadNamePrefix) {
        this.threadNamePrefix = threadNamePrefix;
    }

    public int getAwaitTerminationSeconds() {
        return awaitTerminationSeconds;
    }

    public void setAwaitTerminationSeconds(int awaitTerminationSeconds) {
        this.awaitTerminationSeconds = awaitTerminationSeconds;
    }

    public boolean isWaitForTasksToCompleteOnShutdown() {
        return waitForTasksToCompleteOnShutdown;
    }

    public void setWaitForTasksToCompleteOnShutdown(boolean waitForTasksToCompleteOnShutdown) {
        this.waitForTasksToCompleteOnShutdown = waitForTasksToCompleteOnShutdown;
    }
}
