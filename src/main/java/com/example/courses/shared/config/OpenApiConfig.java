package com.example.courses.shared.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

    @Bean
    OpenAPI coursesOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Courses API")
                .version("v1")
                .description("""
                        Online course platform: catalog, enrollments with limited seats, asynchronous payments \
                        and event-driven certificates. Errors use RFC 9457 (application/problem+json). \
                        Every list endpoint is paginated with page, size (max 100) and sort."""));
    }
}
