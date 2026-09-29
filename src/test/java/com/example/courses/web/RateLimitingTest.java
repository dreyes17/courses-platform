package com.example.courses.web;

import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The shared test context disables rate limiting (the suite logs in hundreds of times from one address), so
 * this class enables it with its own limits, and uses its own client addresses to stay independent of the
 * logins the helpers make from MockMvc's default address.
 */
@TestPropertySource(properties = {
        "app.rate-limit.enabled=true",
        "app.rate-limit.login.capacity=20",
        "app.rate-limit.enrollment.capacity=2",
        "app.rate-limit.mcp.capacity=2"
})
class RateLimitingTest extends ApiTestSupport {

    private static final int LOGIN_CAPACITY = 20;

    @Autowired
    private MeterRegistry meterRegistry;

    @Test
    void loginIsLimitedPerClientAddress() {
        String guesser = "203.0.113.10";
        double rejectedBefore = rejected("login");
        for (int attempt = 0; attempt < LOGIN_CAPACITY; attempt++) {
            assertThat(loginFrom(guesser)).hasStatus(HttpStatus.UNAUTHORIZED);
        }

        MvcTestResult blocked = loginFrom(guesser);

        assertThat(blocked).hasStatus(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(blocked.getResponse().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        assertThat(body(blocked).get("title").asString()).isEqualTo("Too many requests");
        assertThat(Integer.parseInt(blocked.getResponse().getHeader(HttpHeaders.RETRY_AFTER))).isPositive();
        assertThat(rejected("login")).isEqualTo(rejectedBefore + 1);
        // Another client keeps its own budget.
        assertThat(loginFrom("203.0.113.11")).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void enrollmentIsLimitedPerStudentNotPerAddress() {
        String courseId = createPublishedCourse(createInstructor(), 50, BigDecimal.TEN);
        Account first = registerStudent();
        Account second = registerStudent();

        assertThat(enroll(first.token(), courseId, UUID.randomUUID().toString())).hasStatus(HttpStatus.CREATED);
        assertThat(enroll(first.token(), courseId, UUID.randomUUID().toString())).hasStatus(HttpStatus.CONFLICT);
        assertThat(enroll(first.token(), courseId, UUID.randomUUID().toString()))
                .hasStatus(HttpStatus.TOO_MANY_REQUESTS);

        // Same client address as the first student, separate budget.
        assertThat(enroll(second.token(), courseId, UUID.randomUUID().toString())).hasStatus(HttpStatus.CREATED);
    }

    @Test
    void mcpToolCallsAreLimitedPerUser() {
        String token = registerStudent().token();
        String listTools = """
                {"jsonrpc": "2.0", "id": 1, "method": "tools/list"}""";

        assertThat(mcp(token, listTools)).hasStatus(HttpStatus.OK);
        assertThat(mcp(token, listTools)).hasStatus(HttpStatus.OK);
        assertThat(mcp(token, listTools)).hasStatus(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(mcp(registerStudent().token(), listTools)).hasStatus(HttpStatus.OK);
    }

    @Test
    void allowedRequestsReportTheRemainingBudget() {
        MvcTestResult result = loginFrom("203.0.113.20");

        assertThat(result.getResponse().getHeader("X-RateLimit-Remaining"))
                .isEqualTo(String.valueOf(LOGIN_CAPACITY - 1));
    }

    private MvcTestResult loginFrom(String clientAddress) {
        return mvc.post().uri("/api/auth/token")
                .with(request -> {
                    request.setRemoteAddr(clientAddress);
                    return request;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email": "nobody@learn.test", "password": "not-the-password"}""")
                .exchange();
    }

    private MvcTestResult mcp(String token, String json) {
        return mvc.post().uri("/mcp")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json)
                .exchange();
    }

    private double rejected(String rule) {
        return meterRegistry.get("courses.rate_limit.rejected").tag("rule", rule).counter().count();
    }
}
