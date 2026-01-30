package com.huawei.monitoring.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Externalized configuration for RabbitMQ retry behavior.
 * Used by RabbitMQ listeners for message redelivery.
 */
@Configuration
@ConfigurationProperties(prefix = "rabbitmq.retry")
public class RabbitMQRetryConfig {

    private int maxAttempts = 3;
    private long initialBackoffMs = 1000L;
    private double multiplier = 2.0;
    private long maxIntervalMs = 10000L;

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public long getInitialBackoffMs() {
        return initialBackoffMs;
    }

    public void setInitialBackoffMs(long initialBackoffMs) {
        this.initialBackoffMs = initialBackoffMs;
    }

    public double getMultiplier() {
        return multiplier;
    }

    public void setMultiplier(double multiplier) {
        this.multiplier = multiplier;
    }

    public long getMaxIntervalMs() {
        return maxIntervalMs;
    }

    public void setMaxIntervalMs(long maxIntervalMs) {
        this.maxIntervalMs = maxIntervalMs;
    }
}
