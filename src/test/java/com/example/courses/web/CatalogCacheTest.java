package com.example.courses.web;

import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The catalog caches must never show data the application itself has changed: every write path, including the
 * seat counters updated by enrollments, evicts what it touches.
 */
class CatalogCacheTest extends ApiTestSupport {

    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private MeterRegistry meterRegistry;

    @Test
    void categoryReadsAreServedFromTheCacheUntilTheCategoryIsUpdated() {
        String admin = adminToken();
        String categoryId = createCategory();
        String originalName = categoryName(categoryId, admin);

        // A change that bypasses the application proves the second read didn't reach the database...
        jdbcTemplate.update("update categories set name = ? where id = ?", "Changed behind the cache " + unique(),
                UUID.fromString(categoryId));
        assertThat(categoryName(categoryId, admin)).isEqualTo(originalName);

        // ...and a write through the application evicts the entry.
        String renamed = "Renamed " + unique();
        assertThat(put("/api/categories/" + categoryId, admin, """
                {"name": "%s"}""".formatted(renamed))).hasStatus(HttpStatus.OK);
        assertThat(categoryName(categoryId, admin)).isEqualTo(renamed);
    }

    @Test
    void availableSeatsAreUpToDateRightAfterEnrollingAndCancelling() {
        String courseId = createPublishedCourse(createInstructor(), 2, BigDecimal.TEN);
        Account student = registerStudent();
        assertThat(availableSeats(courseId, student.token())).isEqualTo(2);

        var enrollment = enroll(student.token(), courseId, UUID.randomUUID().toString());
        assertThat(enrollment).hasStatus(HttpStatus.CREATED);
        assertThat(availableSeats(courseId, student.token())).isEqualTo(1);

        assertThat(post("/api/enrollments/" + id(enrollment) + "/cancel", student.token(), null))
                .hasStatus(HttpStatus.OK);
        assertThat(availableSeats(courseId, student.token())).isEqualTo(2);
    }

    @Test
    void renamingAnInstructorRefreshesTheCoursesTheyTeach() {
        Account instructor = createInstructor();
        String courseId = createPublishedCourse(instructor, 5, BigDecimal.TEN);
        assertThat(course(courseId, instructor.token()).get("instructorName").asString()).isEqualTo("Grace Hopper");

        assertThat(put("/api/instructors/" + instructor.id(), instructor.token(), """
                {"name": "Grace B. Hopper"}""")).hasStatus(HttpStatus.OK);

        assertThat(course(courseId, instructor.token()).get("instructorName").asString())
                .isEqualTo("Grace B. Hopper");
    }

    @Test
    void aDraftCachedByAnAdminIsStillHiddenFromStudents() {
        String courseId = createDraftCourse(createInstructor(), 5, BigDecimal.TEN);

        assertThat(get("/api/courses/" + courseId, adminToken())).hasStatus(HttpStatus.OK);
        assertThat(get("/api/courses/" + courseId, registerStudent().token())).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void cacheHitsAreExposedAsMetrics() {
        String admin = adminToken();
        String categoryId = createCategory();
        double hitsBefore = cacheGets("categories", "hit");

        categoryName(categoryId, admin);
        categoryName(categoryId, admin);

        assertThat(cacheGets("categories", "hit")).isGreaterThanOrEqualTo(hitsBefore + 1);
    }

    private String categoryName(String categoryId, String token) {
        return body(get("/api/categories/" + categoryId, token)).get("name").asString();
    }

    private int availableSeats(String courseId, String token) {
        return course(courseId, token).get("availableSeats").asInt();
    }

    private JsonNode course(String courseId, String token) {
        return body(get("/api/courses/" + courseId, token));
    }

    private double cacheGets(String cache, String result) {
        FunctionCounter counter = meterRegistry.find("cache.gets").tag("cache", cache).tag("result", result)
                .functionCounter();
        assertThat(counter).as("cache.gets{cache=%s,result=%s}", cache, result).isNotNull();
        return counter.count();
    }
}
