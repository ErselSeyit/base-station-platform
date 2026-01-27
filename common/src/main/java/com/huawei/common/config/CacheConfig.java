package com.huawei.common.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Cache configuration for the platform.
 *
 * Uses in-memory caching by default. For production with multiple instances,
 * configure Redis cache by adding spring-boot-starter-data-redis and setting
 * spring.cache.type=redis in application.yml.
 */
@Configuration
@EnableCaching
@ConditionalOnProperty(name = "spring.cache.enabled", havingValue = "true", matchIfMissing = true)
public class CacheConfig {

    public static final String USERS_CACHE = "users";
    public static final String ALERT_RULES_CACHE = "alertRules";

    @Bean
    @ConditionalOnProperty(name = "spring.cache.type", havingValue = "simple", matchIfMissing = true)
    public CacheManager cacheManager() {
        return new ConcurrentMapCacheManager(USERS_CACHE, ALERT_RULES_CACHE);
    }
}
