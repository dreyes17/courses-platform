package com.example.courses.web;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/** Drives the whole lifecycle through the HTTP API, from catalog setup to course completion. */
class EnrollmentApiTest extends ApiTestSupport {

    @Test
    void studentEnrollsPaysAndCompletesACourseThroughTheApi() {
        String token = unique();
        String categoryId = id(post("/api/categories", """
                {"name": "Backend %s", "description": "Server-side development"}""".formatted(token)));
        String instructorId = id(post("/api/instructors", """
                {"name": "Grace Hopper", "email": "grace-%s@teach.test"}""".formatted(token)));
        MvcTestResult createdCourse = post("/api/courses", """
                {"title": "Event-driven Spring %s", "durationHours": 12, "level": "ADVANCED",
                 "price": 49.90, "capacity": 2, "categoryId": "%s", "instructorId": "%s"}"""
                .formatted(token, categoryId, instructorId));
        assertThat(createdCourse).hasStatus(HttpStatus.CREATED);
        assertThat(createdCourse).headers().containsHeader("Location");
        assertThat(createdCourse).bodyJson().extractingPath("$.status").isEqualTo("DRAFT");
        String courseId = id(createdCourse);

        assertThat(post("/api/courses/" + courseId + "/publish", null))
                .hasStatusOk().bodyJson().extractingPath("$.status").isEqualTo("PUBLISHED");
        String studentId = id(post("/api/students", """
                {"firstName": "Ada", "lastName": "Lovelace", "email": "ada-%s@learn.test"}""".formatted(token)));

        String key = UUID.randomUUID().toString();
        MvcTestResult enrolled = enroll(studentId, courseId, key);
        assertThat(enrolled).hasStatus(HttpStatus.CREATED);
        assertThat(enrolled).bodyJson().extractingPath("$.status").isEqualTo("PENDING_PAYMENT");
        String enrollmentId = id(enrolled);
        assertThat(id(enroll(studentId, courseId, key))).as("retry with the same key").isEqualTo(enrollmentId);

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                assertThat(get("/api/enrollments/" + enrollmentId))
                        .bodyJson().extractingPath("$.status").isEqualTo("ACTIVE"));
        assertThat(put("/api/enrollments/" + enrollmentId + "/progress", """
                {"progress": 100}"""))
                .hasStatusOk().bodyJson().extractingPath("$.status").isEqualTo("COMPLETED");

        MvcTestResult studentsOfCourse = get("/api/courses/" + courseId + "/enrollments");
        assertThat(studentsOfCourse).bodyJson().extractingPath("$.totalElements").isEqualTo(1);
        assertThat(studentsOfCourse).bodyJson().extractingPath("$.content[0].email")
                .isEqualTo("ada-%s@learn.test".formatted(token));
        assertThat(get("/api/students/" + studentId + "/enrollments"))
                .bodyJson().extractingPath("$.content[0].courseTitle").isEqualTo("Event-driven Spring " + token);
        assertThat(get("/api/courses?title=spring " + token + "&withAvailableSeats=true"))
                .bodyJson().extractingPath("$.content[0].availableSeats").isEqualTo(1);
    }
}
