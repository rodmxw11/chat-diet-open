package com.chatdiet.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Metadata for the springdoc-generated OpenAPI spec, served at /v3/api-docs and /swagger-ui.html. */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI chatDietOpenApi() {
        return new OpenAPI().info(new Info()
                .title("chat-diet API")
                .version("0.0.1")
                .description("""
                        HTTP surface for chat-diet's Spring Boot backend. Most functionality (logging \
                        food, weight, exercise, etc.) is driven through the single /api/chat endpoint \
                        via the model's tool-calling framework rather than dedicated REST routes; the \
                        remaining endpoints handle barcode input, file downloads, the food-item CRUD \
                        page, and the header/dashboard screens that don't fit that model."""));
    }
}
