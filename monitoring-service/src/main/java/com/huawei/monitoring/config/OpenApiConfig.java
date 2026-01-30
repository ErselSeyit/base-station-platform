package com.huawei.monitoring.config;

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
                        .title("Monitoring Service API")
                        .version("1.0.0")
                        .description("""
                                Metrics collection and monitoring API for the Base Station Platform.

                                ## Features
                                - Real-time metric recording and querying
                                - Time-range based metric retrieval
                                - Threshold-based alerting
                                - AI-powered diagnostics integration
                                - Batch metric ingestion for edge devices

                                ## Metric Types
                                - CPU_USAGE, MEMORY_USAGE, TEMPERATURE
                                - SIGNAL_STRENGTH, SIGNAL_QUALITY
                                - THROUGHPUT, LATENCY, PACKET_LOSS
                                - And more...
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
