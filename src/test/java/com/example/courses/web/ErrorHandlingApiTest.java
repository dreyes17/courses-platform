package com.example.courses.web;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ErrorHandlingApiTest extends ApiTestSupport {

    @Test
    void invalidBodyReturns400WithFieldErrors() {
        var result = post("/api/courses", adminToken(), """
                {"title": "", "durationHours": 0, "price": -1}""");

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(result).bodyJson().extractingPath("$.title").isEqualTo("Validation failed");
        assertThat(result).bodyJson().extractingPath("$.errors").asMap()
                .containsKeys("title", "durationHours", "price", "level", "capacity", "categoryId", "instructorId");
    }

    @Test
    void unknownResourceReturns404Problem() {
        var result = get("/api/courses/" + UUID.randomUUID(), adminToken());

        assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(result).hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(result).bodyJson().extractingPath("$.status").isEqualTo(404);
    }

    @Test
    void enrollingWithoutIdempotencyKeyIsRejected() {
        var result = post("/api/enrollments", registerStudent().token(), """
                {"courseId": "%s"}""".formatted(UUID.randomUUID()));

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void fullCourseReturns409() {
        String courseId = createPublishedCourse(createInstructor(), 1, BigDecimal.TEN);
        assertThat(enroll(registerStudent().token(), courseId, UUID.randomUUID().toString()))
                .hasStatus(HttpStatus.CREATED);

        var result = enroll(registerStudent().token(), courseId, UUID.randomUUID().toString());

        assertThat(result).hasStatus(HttpStatus.CONFLICT);
        assertThat(result).bodyJson().extractingPath("$.detail").asString().contains("no available seats");
    }

    @Test
    void reusingIdempotencyKeyForAnotherRequestReturns422() {
        Account instructor = createInstructor();
        String studentToken = registerStudent().token();
        String key = UUID.randomUUID().toString();
        enroll(studentToken, createPublishedCourse(instructor, 5, BigDecimal.TEN), key);

        var result = enroll(studentToken, createPublishedCourse(instructor, 5, BigDecimal.TEN), key);

        assertThat(result).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
    }

    @Test
    void invalidStateTransitionReturns409() {
        Account instructor = createInstructor();
        String courseId = createPublishedCourse(instructor, 5, BigDecimal.TEN);

        assertThat(post("/api/courses/" + courseId + "/publish", instructor.token(), null))
                .hasStatus(HttpStatus.CONFLICT);
    }

    @Test
    void duplicateCategoryNameReturns409() {
        String body = """
                {"name": "Data %s"}""".formatted(unique());
        post("/api/categories", adminToken(), body);

        assertThat(post("/api/categories", adminToken(), body)).hasStatus(HttpStatus.CONFLICT);
    }

    @Test
    void unknownSortPropertyReturns400() {
        assertThat(get("/api/courses?sort=doesNotExist", adminToken())).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void pageSizeIsCappedAt100() {
        assertThat(get("/api/categories?size=5000", adminToken()))
                .bodyJson().extractingPath("$.size").isEqualTo(100);
    }
}
