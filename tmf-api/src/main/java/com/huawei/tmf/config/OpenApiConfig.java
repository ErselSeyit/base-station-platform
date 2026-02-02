package com.huawei.tmf.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.List;

/**
 * OpenAPI configuration for TMF APIs.
 */
@Configuration
public class OpenApiConfig {

    @Value("${tmf.base-url:http://localhost:8086}")
    private String baseUrl;

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("TM Forum Open APIs")
                .version("4.0.0")
                .description("""
                    Implementation of TM Forum Open APIs for telecom service management.

                    ## Implemented APIs

                    ### TMF638 - Service Inventory Management
                    Provides standardized mechanism for managing services throughout their lifecycle.

                    ### TMF639 - Resource Inventory Management
                    Provides standardized mechanism for managing resources (network elements, devices).

                    ### TMF642 - Alarm Management
                    Provides standardized mechanism for managing alarms and events.

                    ## Pagination
                    All list endpoints support pagination via `offset` and `limit` query parameters.
                    Response headers include `X-Total-Count` and `X-Result-Count`.

                    ## Filtering
                    List endpoints support filtering via query parameters specific to each resource type.

                    ## Notification Hub
                    Each API includes `/hub` endpoints for registering webhook callbacks for events.
                    """)
                .contact(new Contact()
                    .name("Base Station Platform Team")
                    .email("support@example.com"))
                .license(new License()
                    .name("Apache 2.0")
                    .url("https://www.apache.org/licenses/LICENSE-2.0")))
            .servers(List.of(
                new Server().url(baseUrl).description("TMF API Server")
            ))
            .tags(Arrays.asList(
                new Tag().name("TMF638 - Service Inventory")
                    .description("Service Inventory Management API endpoints"),
                new Tag().name("TMF639 - Resource Inventory")
                    .description("Resource Inventory Management API endpoints"),
                new Tag().name("TMF642 - Alarm Management")
                    .description("Alarm Management API endpoints")
            ));
    }
}
