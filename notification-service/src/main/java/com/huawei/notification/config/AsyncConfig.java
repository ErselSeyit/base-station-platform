package com.huawei.notification.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Async executor configuration for notification processing.
 * Uses externalized configuration from ThreadPoolConfig for single source of truth.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * Bean name for the notification executor.
     */
    public static final String NOTIFICATION_EXECUTOR = "notificationExecutor";

    private final ThreadPoolConfig config;

    public AsyncConfig(ThreadPoolConfig config) {
        this.config = config;
    }

    /**
     * Creates a ThreadPoolTaskExecutor for async notification processing.
     *
     * <p>Features:
     * <ul>
     *   <li>Configurable pool size via application.yml</li>
     *   <li>Graceful shutdown - waits for tasks to complete</li>
     *   <li>CallerRunsPolicy - executes in caller thread if queue is full</li>
     * </ul>
     */
    @Bean(name = NOTIFICATION_EXECUTOR)
    public Executor notificationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(config.getCoreSize());
        executor.setMaxPoolSize(config.getMaxSize());
        executor.setQueueCapacity(config.getQueueCapacity());
        executor.setThreadNamePrefix(config.getThreadNamePrefix());

        // Graceful shutdown configuration
        executor.setWaitForTasksToCompleteOnShutdown(config.isWaitForTasksToCompleteOnShutdown());
        executor.setAwaitTerminationSeconds(config.getAwaitTerminationSeconds());

        // Rejection policy: execute in caller thread instead of throwing exception
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());

        executor.initialize();
        return executor;
    }
}
