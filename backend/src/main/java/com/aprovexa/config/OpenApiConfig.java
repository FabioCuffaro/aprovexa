package com.aprovexa.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI aprovexaOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Aprovexa Request Management API")
                .version("v1")
                .description("REST API for creating, reviewing and transitioning internal requests."));
    }
}
