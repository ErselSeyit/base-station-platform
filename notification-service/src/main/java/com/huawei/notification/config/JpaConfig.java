package com.huawei.notification.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * JPA configuration for the notification service.
 * 
 * <p>This is in a separate class to avoid @EnableJpaAuditing interfering
 * with @WebMvcTest slices that don't load JPA entities.
 */
@Configuration
@EnableJpaAuditing
public class JpaConfig {
}
