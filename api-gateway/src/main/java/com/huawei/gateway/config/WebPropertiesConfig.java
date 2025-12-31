package com.huawei.gateway.config;

import org.springframework.boot.autoconfigure.web.WebProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for web properties used by the global exception handler.
 * 
 * <p>Spring Cloud Gateway is a reactive application and doesn't auto-configure
 * WebProperties.Resources by default. This configuration provides that bean
 * for the GlobalExceptionHandler which extends AbstractErrorWebExceptionHandler.
 */
@Configuration
public class WebPropertiesConfig {

    @Bean
    public WebProperties.Resources resources() {
        return new WebProperties.Resources();
    }
}
