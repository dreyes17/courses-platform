package io.github.dreyes17.courses.shared.config;

import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MapSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.method.HandlerMethod;

import java.util.Arrays;
import java.util.Map;

@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

    private static final String BEARER_SCHEME = "bearerAuth";
    private static final String PROBLEM_SCHEMA = "ProblemDetail";

    @Bean
    OpenAPI coursesOpenApi() {
        return new OpenAPI()
                .components(new Components()
                        .addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT"))
                        .addSchemas(PROBLEM_SCHEMA, problemSchema()))
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

    /**
     * Documents every error an operation can return, so a client can integrate from the specification alone. The
     * errors that follow from the operation itself are added here: 400 when it takes input, 401 when it needs a
     * token, 403 when it has a role or ownership rule, 404 when it addresses a resource by id. Those that depend on
     * business rules (409, 422, 429...) are declared on each endpoint with {@code @ApiResponse}. All of them get
     * the problem+json body, and the responses are listed in status order.
     */
    @Bean
    OperationCustomizer errorResponses() {
        return (operation, handlerMethod) -> {
            ApiResponses responses = operation.getResponses();
            boolean takesInput = operation.getRequestBody() != null
                    || (operation.getParameters() != null && !operation.getParameters().isEmpty());
            if (takesInput) {
                responses.putIfAbsent("400", new ApiResponse().description("Invalid input: a field fails "
                        + "validation (see errors), malformed JSON or an invalid parameter"));
            }
            if (requiresToken(handlerMethod)) {
                responses.putIfAbsent("401", new ApiResponse().description("Missing, invalid or expired bearer token"));
            }
            if (handlerMethod.hasMethodAnnotation(PreAuthorize.class)) {
                responses.putIfAbsent("403", new ApiResponse().description(
                        "The caller's role or ownership doesn't allow it (also for ids that don't exist)"));
            }
            if (addressesResourceById(handlerMethod)) {
                responses.putIfAbsent("404", new ApiResponse().description("Resource not found"));
            }

            ApiResponses sorted = new ApiResponses();
            responses.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
                if (entry.getKey().startsWith("4") || entry.getKey().startsWith("5")) {
                    entry.getValue().setContent(problemContent());
                }
                sorted.addApiResponse(entry.getKey(), entry.getValue());
            });
            operation.setResponses(sorted);
            return operation;
        };
    }

    /** Public endpoints opt out of the global bearer requirement with an empty {@code @SecurityRequirements}. */
    private static boolean requiresToken(HandlerMethod handlerMethod) {
        return !handlerMethod.hasMethodAnnotation(SecurityRequirements.class)
                && !AnnotatedElementUtils.hasAnnotation(handlerMethod.getBeanType(), SecurityRequirements.class);
    }

    private static boolean addressesResourceById(HandlerMethod handlerMethod) {
        return Arrays.stream(handlerMethod.getMethodParameters())
                .anyMatch(parameter -> parameter.hasParameterAnnotation(PathVariable.class));
    }

    private static Content problemContent() {
        return new Content().addMediaType(org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                new MediaType().schema(new Schema<>().$ref("#/components/schemas/" + PROBLEM_SCHEMA)));
    }

    /** The body GlobalExceptionHandler and ProblemDetailsSecurityHandler write for every error. */
    private static Schema<?> problemSchema() {
        return new ObjectSchema()
                .description("RFC 9457 problem details")
                .addProperty("type", new StringSchema().format("uri").example("about:blank"))
                .addProperty("title", new StringSchema().example("Conflict with current state"))
                .addProperty("status", new IntegerSchema().example(409))
                .addProperty("detail", new StringSchema().example("Course 6f1c0e4a-... is full"))
                .addProperty("instance", new StringSchema().format("uri").example("/api/enrollments"))
                .addProperty("errors", new MapSchema()
                        .additionalProperties(new StringSchema())
                        .description("Only on validation errors: the message for each invalid field")
                        .example(Map.of("progress", "must be less than or equal to 100")));
    }
}
