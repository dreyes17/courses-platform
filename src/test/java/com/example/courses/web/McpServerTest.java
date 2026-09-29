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
    /**
     * Logged in once for the whole class: this context enforces the real login rate limit (10 per minute per IP),
     * which a fresh admin login per call would exhaust.
     */
    private static String adminToken;

    @LocalServerPort
    private int port;
    @Autowired
    private JsonMapper jsonMapper;

    @AfterEach
    void closeClients() {
        clients.forEach(McpSyncClient::close);
    }

    @Test
    void toolsCoverCategoriesCoursesInstructorsStudentsAndEnrollments() {
        List<Tool> tools = client(adminToken()).listTools().tools();

        assertThat(tools).extracting(Tool::name).containsExactlyInAnyOrder(
                "list_categories", "get_category", "create_category", "update_category", "archive_category",
                "activate_category", "delete_category",
                "list_courses", "search_courses", "get_course", "create_course", "update_course", "publish_course",
                "archive_course", "delete_course",
                "create_instructor", "list_instructors", "get_instructor", "update_instructor", "delete_instructor",
                "list_students", "get_student",
                "enroll_student", "get_enrollment", "update_enrollment_progress", "cancel_enrollment",
                "list_enrollments", "list_students_by_course", "list_courses_by_student",
                "get_enrollment_payment", "get_enrollment_certificate", "verify_certificate");
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
        assertThat(ok(admin, "list_students_by_course", Map.of("courseId", courseId, "status", "ACTIVE"))
                .get("totalElements").asInt()).isEqualTo(1);
        assertThat(ok(admin, "list_students_by_course", Map.of("courseId", courseId, "status", "CANCELLED"))
                .get("totalElements").asInt()).isZero();
        assertThat(ok(admin, "list_enrollments", Map.of("courseId", courseId)).get("content").get(0)
                .get("enrollmentId").asString()).isEqualTo(enrollmentId);
        assertThat(ok(student, "get_enrollment_payment", Map.of("enrollmentId", enrollmentId))
                .get("status").asString()).isEqualTo("CONFIRMED");

        // Completing it issues the certificate asynchronously; its code is then verifiable.
        ok(student, "update_enrollment_progress", Map.of("enrollmentId", enrollmentId, "progress", 100));
        String code = await().atMost(Duration.ofSeconds(15)).ignoreExceptions().until(
                () -> ok(student, "get_enrollment_certificate", Map.of("enrollmentId", enrollmentId))
                        .get("code").asString(), value -> value != null);
        assertThat(ok(admin, "verify_certificate", Map.of("code", code)).get("courseTitle").asString())
                .isEqualTo(title);
    }

    @Test
    void anAiClientCanManageTheCatalogThroughTools() {
        McpSyncClient admin = client(adminToken());
        String categoryId = ok(admin, "create_category", Map.of("name", "MCP " + unique())).get("id").asString();
        String renamed = "Renamed " + unique();
        assertThat(ok(admin, "update_category", Map.of("categoryId", categoryId, "name", renamed))
                .get("name").asString()).isEqualTo(renamed);
        assertThat(ok(admin, "archive_category", Map.of("categoryId", categoryId)).get("status").asString())
                .isEqualTo("ARCHIVED");
        assertThat(ok(admin, "activate_category", Map.of("categoryId", categoryId)).get("status").asString())
                .isEqualTo("ACTIVE");

        String email = "mcp-instructor-" + unique() + "@teach.test";
        String instructorId = ok(admin, "create_instructor", Map.of("name", "Grace Hopper", "email", email,
                "password", PASSWORD)).get("id").asString();
        assertThat(ok(admin, "list_instructors", Map.of("email", email)).get("totalElements").asInt()).isEqualTo(1);
        McpSyncClient instructor = client(login(email, PASSWORD));
        assertThat(ok(instructor, "update_instructor", Map.of("instructorId", instructorId, "name", "Grace B. Hopper",
                "bio", "COBOL")).get("bio").asString()).isEqualTo("COBOL");
        assertThat(ok(instructor, "get_instructor", Map.of("instructorId", instructorId)).get("name").asString())
                .isEqualTo("Grace B. Hopper");

        String courseId = ok(instructor, "create_course", Map.of("title", "Draft " + unique(), "durationHours", 8,
                "level", "BEGINNER", "price", 25, "capacity", 3, "categoryId", categoryId,
                "instructorId", instructorId)).get("id").asString();
        JsonNode updated = ok(instructor, "update_course", Map.of("courseId", courseId, "title", "Updated",
                "durationHours", 12, "level", "ADVANCED", "price", 30, "capacity", 10));
        assertThat(updated.get("level").asString()).isEqualTo("ADVANCED");
        assertThat(updated.get("capacity").asInt()).isEqualTo(10);

        // Deletes answer with the removed id; a category with courses must be archived instead.
        assertThat(error(admin, "delete_category", Map.of("categoryId", categoryId)))
                .contains("still has courses");
        assertThat(ok(instructor, "delete_course", Map.of("courseId", courseId)).get("deletedId").asString())
                .isEqualTo(courseId);
        assertThat(ok(admin, "delete_category", Map.of("categoryId", categoryId)).get("deletedId").asString())
                .isEqualTo(categoryId);
        assertThat(error(admin, "get_category", Map.of("categoryId", categoryId))).endsWith("not found");
        assertThat(ok(admin, "delete_instructor", Map.of("instructorId", instructorId)).get("deletedId").asString())
                .isEqualTo(instructorId);
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
        assertThat(error(student, "list_instructors", Map.of()))
                .isEqualTo("You are not allowed to perform this operation");

        // Another instructor doesn't see the draft either, and can't change it.
        String otherEmail = "mcp-instructor-" + unique() + "@teach.test";
        ok(admin, "create_instructor", Map.of("name", "Other", "email", otherEmail, "password", PASSWORD));
        McpSyncClient otherInstructor = client(login(otherEmail, PASSWORD));
        assertThat(error(otherInstructor, "get_course", Map.of("courseId", draftId)))
                .isEqualTo("Course " + draftId + " not found");
        assertThat(ok(otherInstructor, "search_courses", Map.of("status", "DRAFT", "size", 100)).get("content")
                .valueStream().map(course -> course.get("id").asString())).doesNotContain(draftId);
        assertThat(error(otherInstructor, "delete_course", Map.of("courseId", draftId)))
                .isEqualTo("You are not allowed to perform this operation");
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
        if (adminToken == null) {
            adminToken = login(RealServerTest.ADMIN_EMAIL, RealServerTest.ADMIN_PASSWORD);
        }
        return adminToken;
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
