package io.github.dreyes17.courses.web;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/** Drives the whole lifecycle through the HTTP API, each step performed by the role that owns it. */
class EnrollmentApiTest extends ApiTestSupport {

    @Test
    void studentEnrollsPaysAndCompletesACourseThroughTheApi() {
        String token = unique();
        String adminToken = adminToken();
        String categoryId = id(post("/api/categories", adminToken, """
                {"name": "Backend %s", "description": "Server-side development"}""".formatted(token)));
        Account instructor = createInstructor();

        MvcTestResult createdCourse = post("/api/courses", instructor.token(), """
                {"title": "Event-driven Spring %s", "durationHours": 12, "level": "ADVANCED",
                 "price": 49.90, "capacity": 2, "categoryId": "%s", "instructorId": "%s"}"""
                .formatted(token, categoryId, instructor.id()));
        assertThat(createdCourse).hasStatus(HttpStatus.CREATED);
        assertThat(createdCourse).headers().containsHeader("Location");
        assertThat(createdCourse).bodyJson().extractingPath("$.status").isEqualTo("DRAFT");
        String courseId = id(createdCourse);
        assertThat(post("/api/courses/" + courseId + "/publish", instructor.token(), null))
                .hasStatusOk().bodyJson().extractingPath("$.status").isEqualTo("PUBLISHED");

        Account student = registerStudent();
        String key = UUID.randomUUID().toString();
        MvcTestResult enrolled = enroll(student.token(), courseId, key);
        assertThat(enrolled).hasStatus(HttpStatus.CREATED);
        assertThat(enrolled).bodyJson().extractingPath("$.status").isEqualTo("PENDING_PAYMENT");
        assertThat(enrolled).bodyJson().extractingPath("$.studentId").isEqualTo(student.id());
        String enrollmentId = id(enrolled);
        assertThat(id(enroll(student.token(), courseId, key))).as("retry with the same key").isEqualTo(enrollmentId);

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                assertThat(get("/api/enrollments/" + enrollmentId, student.token()))
                        .bodyJson().extractingPath("$.status").isEqualTo("ACTIVE"));
        assertThat(put("/api/enrollments/" + enrollmentId + "/progress", student.token(), """
                {"progress": 100}"""))
                .hasStatusOk().bodyJson().extractingPath("$.status").isEqualTo("COMPLETED");

        MvcTestResult studentsOfCourse = get("/api/courses/" + courseId + "/enrollments", instructor.token());
        assertThat(studentsOfCourse).bodyJson().extractingPath("$.totalElements").isEqualTo(1);
        assertThat(studentsOfCourse).bodyJson().extractingPath("$.content[0].email").isEqualTo(student.email());
        assertThat(get("/api/students/" + student.id() + "/enrollments", student.token()))
                .bodyJson().extractingPath("$.content[0].courseTitle").isEqualTo("Event-driven Spring " + token);
        assertThat(get("/api/courses?title=spring " + token + "&withAvailableSeats=true", student.token()))
                .bodyJson().extractingPath("$.content[0].availableSeats").isEqualTo(1);
    }
}
