package com.example.courses.web;

import com.example.courses.support.RealServerTest;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.LIST;
import static org.awaitility.Awaitility.await;

/**
 * Drives the MCP server at /mcp with the official MCP Java client, as an AI client would, over real HTTP and
 * with bearer tokens issued by the API itself.
 */
@RealServerTest
class McpServerTest {

    private static final String PASSWORD = "correct-horse-battery";

    private final HttpClient http = HttpClient.newHttpClient();
    private final List<McpSyncClient> clients = new ArrayList<>();

    @LocalServerPort
    private int port;
    @Autowired
    private JsonMapper jsonMapper;

    @AfterEach
    void closeClients() {
        clients.forEach(McpSyncClient::close);
    }

    @Test
    void toolsCoverCategoriesCoursesStudentsAndEnrollments() {
        List<Tool> tools = client(adminToken()).listTools().tools();

        assertThat(tools).extracting(Tool::name).containsExactlyInAnyOrder(
                "list_categories", "get_category", "create_category",
                "list_courses", "search_courses", "get_course", "create_course", "publish_course", "archive_course",
                "list_students", "get_student",
                "enroll_student", "get_enrollment", "update_enrollment_progress", "cancel_enrollment",
                "list_students_by_course", "list_courses_by_student");
        assertThat(tools).allSatisfy(tool -> assertThat(tool.description()).isNotBlank());
        Tool enroll = tools.stream().filter(tool -> tool.name().equals("enroll_student")).findFirst().orElseThrow();
        assertThat(enroll.inputSchema().get("required")).asInstanceOf(LIST)
                .containsExactlyInAnyOrder("courseId", "idempotencyKey");
    }

    @Test
    void anAiClientCanRunTheWholeEnrollmentFlowThroughTools() {
        McpSyncClient admin = client(adminToken());
        String title = "MCP course " + unique();
        String categoryId = ok(admin, "create_category", Map.of("name", "MCP " + unique())).get("id").asString();
        String instructorId = createInstructor();
        String courseId = ok(admin, "create_course", Map.of("title", title, "durationHours", 8,
                "level", "BEGINNER", "price", 25, "capacity", 3, "categoryId", categoryId,
                "instructorId", instructorId)).get("id").asString();
        assertThat(ok(admin, "publish_course", Map.of("courseId", courseId)).get("status").asString())
                .isEqualTo("PUBLISHED");

        McpSyncClient student = client(studentToken());
        JsonNode found = ok(student, "search_courses", Map.of("title", title));
        assertThat(found.get("content").get(0).get("id").asString()).isEqualTo(courseId);

        String key = UUID.randomUUID().toString();
        JsonNode enrollment = ok(student, "enroll_student", Map.of("courseId", courseId, "idempotencyKey", key));
        assertThat(enrollment.get("status").asString()).isEqualTo("PENDING_PAYMENT");
        String enrollmentId = enrollment.get("id").asString();
        // A retry with the same key returns the same enrollment instead of taking a second seat.
        assertThat(ok(student, "enroll_student", Map.of("courseId", courseId, "idempotencyKey", key))
                .get("id").asString()).isEqualTo(enrollmentId);

        // The payment is confirmed asynchronously through RabbitMQ, exactly as for a REST enrollment.
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(
                ok(student, "get_enrollment", Map.of("enrollmentId", enrollmentId)).get("status").asString())
                .isEqualTo("ACTIVE"));
        assertThat(ok(admin, "get_course", Map.of("courseId", courseId)).get("availableSeats").asInt()).isEqualTo(2);
    }

    @Test
    void toolsApplyTheSameAuthorizationAsTheApi() {
        McpSyncClient admin = client(adminToken());
        String categoryId = ok(admin, "create_category", Map.of("name", "MCP " + unique())).get("id").asString();
        String draftId = ok(admin, "create_course", Map.of("title", "Draft " + unique(), "durationHours", 8,
                "level", "BEGINNER", "price", 25, "capacity", 3, "categoryId", categoryId,
                "instructorId", createInstructor())).get("id").asString();
        McpSyncClient student = client(studentToken());

        assertThat(error(student, "create_category", Map.of("name", "Not allowed " + unique())))
                .isEqualTo("You are not allowed to perform this operation");
        assertThat(error(student, "list_students", Map.of()))
                .isEqualTo("You are not allowed to perform this operation");
        assertThat(error(student, "get_course", Map.of("courseId", draftId)))
                .isEqualTo("Course " + draftId + " not found");
    }

    @Test
    void invalidArgumentsAreRejectedBeforeReachingTheUseCase() {
        McpSyncClient admin = client(adminToken());

        assertThat(error(admin, "create_category", Map.of("name", " ")))
                .isEqualTo("Invalid arguments: name must not be blank");
        assertThat(error(admin, "update_enrollment_progress",
                Map.of("enrollmentId", UUID.randomUUID().toString(), "progress", 150)))
                .isEqualTo("Invalid arguments: progress must be less than or equal to 100");
    }

    @Test
    void theMcpEndpointRequiresABearerToken() throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/mcp"))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json, text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString("""
                        {"jsonrpc": "2.0", "id": 1, "method": "tools/list"}"""))
                .build();

        assertThat(http.send(request, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(401);
    }

    private McpSyncClient client(String token) {
        var transport = HttpClientStreamableHttpTransport.builder("http://localhost:" + port)
                .endpoint("/mcp")
                .requestBuilder(HttpRequest.newBuilder().header("Authorization", "Bearer " + token))
                .build();
        McpSyncClient client = McpClient.sync(transport).requestTimeout(Duration.ofSeconds(15)).build();
        clients.add(client);
        client.initialize();
        return client;
    }

    private JsonNode ok(McpSyncClient client, String tool, Map<String, Object> arguments) {
        CallToolResult result = client.callTool(new CallToolRequest(tool, arguments));
        assertThat(result.isError()).as("%s failed: %s", tool, text(result)).isNotEqualTo(Boolean.TRUE);
        return jsonMapper.readTree(text(result));
    }

    private String error(McpSyncClient client, String tool, Map<String, Object> arguments) {
        CallToolResult result = client.callTool(new CallToolRequest(tool, arguments));
        assertThat(result.isError()).as("%s should have failed: %s", tool, text(result)).isTrue();
        return text(result).strip();
    }

    private static String text(CallToolResult result) {
        return ((TextContent) result.content().getFirst()).text();
    }

    private String adminToken() {
        return login(RealServerTest.ADMIN_EMAIL, RealServerTest.ADMIN_PASSWORD);
    }

    private String studentToken() {
        String email = "mcp-student-" + unique() + "@learn.test";
        post("/api/auth/register", null, """
                {"firstName": "Ada", "lastName": "Lovelace", "email": "%s", "password": "%s"}"""
                .formatted(email, PASSWORD));
        return login(email, PASSWORD);
    }

    private String createInstructor() {
        return post("/api/instructors", adminToken(), """
                {"name": "Grace Hopper", "email": "mcp-instructor-%s@teach.test", "password": "%s"}"""
                .formatted(unique(), PASSWORD)).get("id").asString();
    }

    private String login(String email, String password) {
        return post("/api/auth/token", null, """
                {"email": "%s", "password": "%s"}""".formatted(email, password)).get("accessToken").asString();
    }

    private JsonNode post(String path, String token, String json) {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json));
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        try {
            HttpResponse<String> response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).as("%s: %s", path, response.body()).isBetween(200, 299);
            return jsonMapper.readTree(response.body());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String unique() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
