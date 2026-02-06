package com.huawei.notification.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .addServersItem(new Server().url("/").description("Default Server"))
                .info(new Info()
                        .title("Notification Service API")
                        .version("1.0.0")
                        .description("""
                                Notification management API for the Base Station Platform.

                                ## Features
                                - Create and send notifications for base stations
                                - Async processing of pending notifications
                                - Filter by station or status
                                - RabbitMQ integration for async delivery

                                ## Notification Types
                                - ALERT: Critical system alerts
                                - WARNING: Non-critical warnings
                                - INFO: Informational messages
                                - MAINTENANCE: Scheduled maintenance notices
                                """)
                        .contact(new Contact()
                                .name("API Support")
                                .email("support@example.com"))
                        .license(new License()
                                .name("MIT License")
                                .url("https://opensource.org/licenses/MIT")))
                .components(new Components()
                        .addSecuritySchemes("bearerAuth", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("JWT token obtained from auth-service")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }
}
