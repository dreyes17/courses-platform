package com.example.courses.shared.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

    private static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    OpenAPI coursesOpenApi() {
        return new OpenAPI()
                .components(new Components().addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME))
                .info(new Info()
                        .title("Courses API")
                        .version("v1")
                        .description("""
                                Online course platform: catalog, enrollments with limited seats, asynchronous \
                                payments and event-driven certificates. Errors use RFC 9457 \
                                (application/problem+json). Every list endpoint is paginated with page, size \
                                (max 100) and sort. Get a token from POST /api/auth/token and send it as \
                                "Authorization: Bearer <token>"."""));
    }
}
