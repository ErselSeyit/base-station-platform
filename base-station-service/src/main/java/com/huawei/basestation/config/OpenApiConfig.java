package com.huawei.basestation.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .addServersItem(new Server().url("/").description("Default Server"))
                .info(new Info()
                        .title("Base Station Management API")
                        .version("1.0.0")
                        .description("""
                                RESTful API for managing telecommunications base stations.
                                
                                ## Features
                                - CRUD operations for base stations
                                - Geographic search (find stations in area)
                                - Status-based filtering
                                - Station type categorization
                                
                                ## Station Types
                                - MACRO_CELL: Large coverage area towers
                                - MICRO_CELL: Medium coverage for urban areas
                                - PICO_CELL: Small indoor/outdoor cells
                                - FEMTO_CELL: Home/small office cells
                                """)
                        .contact(new Contact()
                                .name("API Support")
                                .email("support@example.com"))
                        .license(new License()
                                .name("MIT License")
                                .url("https://opensource.org/licenses/MIT")));
    }
}

