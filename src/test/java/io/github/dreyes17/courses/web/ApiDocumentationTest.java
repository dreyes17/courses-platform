package io.github.dreyes17.courses.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** The OpenAPI document states the real success code and every error of each operation, as problem+json. */
class ApiDocumentationTest extends ApiTestSupport {

    private JsonNode spec;

    @BeforeEach
    void loadSpecification() {
        spec = body(get("/v3/api-docs", null));
    }

    @Test
    void enrollmentDocumentsItsSuccessCodeAndEveryError() {
        assertThat(responseCodes("/api/enrollments", "post"))
                .containsExactly("201", "400", "401", "403", "404", "409", "422", "429");
    }

    @Test
    void successCodesMatchWhatTheEndpointsReturn() {
        assertThat(responseCodes("/api/categories", "post")).startsWith("201").doesNotContain("200");
        assertThat(responseCodes("/api/auth/register", "post")).startsWith("201").doesNotContain("200");
        assertThat(responseCodes("/api/categories/{id}", "delete")).startsWith("204").doesNotContain("200");
        assertThat(responseCodes("/api/courses/{id}", "get")).startsWith("200");
    }

    @Test
    void everyOperationDocumentsExactlyOneSuccessResponseWithItsBody() {
        List<String> wrong = new ArrayList<>();
        for (var path : spec.get("paths").properties()) {
            for (var method : path.getValue().properties()) {
                var successes = method.getValue().get("responses").propertyNames().stream()
                        .filter(code -> code.startsWith("2")).toList();
                boolean hasBody = successes.size() == 1 && (successes.getFirst().equals("204")
                        || method.getValue().get("responses").get(successes.getFirst()).has("content"));
                if (!hasBody) {
                    wrong.add(method.getKey() + " " + path.getKey() + " " + successes);
                }
            }
        }
        assertThat(wrong).isEmpty();
    }

    @Test
    void genericErrorsFollowFromTheOperation() {
        assertThat(responseCodes("/api/categories", "get"))
                .as("takes filters and needs a token, but has no role rule nor id")
                .containsExactly("200", "400", "401");
        assertThat(responseCodes("/api/auth/token", "post"))
                .as("public: its 401 is the documented wrong-credentials case")
                .containsExactly("200", "400", "401", "429");
        assertThat(operation("/api/auth/token", "post").get("responses").get("401").get("description").asString())
                .isEqualTo("Invalid email or password");
        assertThat(responseCodes("/api/certificates/{code}", "get"))
                .as("public, no role rule, addresses a certificate by its code")
                .containsExactly("200", "400", "404");
        assertThat(operation("/api/certificates/{code}", "get").get("security")).as("no bearer token").isEmpty();
    }

    @Test
    void everyErrorIsDocumentedAsAProblemDetail() {
        assertThat(spec.get("components").get("schemas").get("ProblemDetail").get("properties").propertyNames())
                .contains("type", "title", "status", "detail", "instance", "errors");
        List<String> undocumented = new ArrayList<>();
        for (var path : spec.get("paths").properties()) {
            for (var method : path.getValue().properties()) {
                for (var response : method.getValue().get("responses").properties()) {
                    JsonNode schema = response.getValue().path("content").path("application/problem+json")
                            .path("schema").path("$ref");
                    if (response.getKey().charAt(0) >= '4'
                            && !schema.asString("").equals("#/components/schemas/ProblemDetail")) {
                        undocumented.add(method.getKey() + " " + path.getKey() + " " + response.getKey());
                    }
                }
            }
        }
        assertThat(undocumented).isEmpty();
    }

    private JsonNode operation(String path, String method) {
        return spec.get("paths").get(path).get(method);
    }

    private List<String> responseCodes(String path, String method) {
        return List.copyOf(operation(path, method).get("responses").propertyNames());
    }
}
