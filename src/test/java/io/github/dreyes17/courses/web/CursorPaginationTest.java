package io.github.dreyes17.courses.web;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** GET /api/courses/scroll: keyset pagination, newest first. */
class CursorPaginationTest extends ApiTestSupport {

    @Test
    void scrollingVisitsEveryCourseExactlyOnceNewestFirst() {
        Account instructor = createInstructor();
        String categoryId = createCategory();
        List<String> created = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            created.add(createPublishedCourseIn(categoryId, instructor));
        }
        String student = registerStudent().token();

        List<String> seen = new ArrayList<>();
        List<Integer> pageSizes = new ArrayList<>();
        String cursor = null;
        do {
            JsonNode page = scroll(student, categoryId, cursor, 2);
            page.get("content").forEach(course -> seen.add(course.get("id").asString()));
            pageSizes.add(page.get("content").size());
            cursor = page.get("nextCursor").isNull() ? null : page.get("nextCursor").asString();
        } while (cursor != null);

        assertThat(pageSizes).containsExactly(2, 2, 1);
        assertThat(seen).containsExactlyElementsOf(created.reversed());
    }

    @Test
    void aCourseCreatedWhileScrollingIsNeitherRepeatedNorSkipped() {
        Account instructor = createInstructor();
        String categoryId = createCategory();
        List<String> created = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            created.add(createPublishedCourseIn(categoryId, instructor));
        }
        String admin = adminToken();
        JsonNode first = scroll(admin, categoryId, null, 2);
        assertThat(ids(first)).containsExactly(created.get(3), created.get(2));

        createPublishedCourseIn(categoryId, instructor);

        JsonNode second = scroll(admin, categoryId, first.get("nextCursor").asString(), 2);
        assertThat(ids(second)).containsExactly(created.get(1), created.get(0));
        // The same situation with page numbers: the new course pushes everything down one row, so page 1
        // repeats a course the client has already seen.
        JsonNode offsetPage = body(get("/api/courses?categoryId=" + categoryId + "&page=1&size=2&sort=createdAt,desc",
                admin));
        assertThat(ids(offsetPage)).contains(created.get(2));
    }

    @Test
    void studentsOnlyScrollThroughPublishedCourses() {
        Account instructor = createInstructor();
        String categoryId = createCategory();
        String published = createPublishedCourseIn(categoryId, instructor);
        createCourseIn(categoryId, instructor);

        assertThat(ids(scroll(registerStudent().token(), categoryId, null, 10))).containsExactly(published);
        assertThat(ids(scroll(adminToken(), categoryId, null, 10))).hasSize(2);
    }

    @Test
    void aCursorThatWasNotIssuedByTheApiIsRejected() {
        var result = get("/api/courses/scroll?cursor=not-a-real-cursor", adminToken());

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(body(result).get("title").asString()).isEqualTo("Invalid cursor");
    }

    @Test
    void pageSizeIsBounded() {
        assertThat(get("/api/courses/scroll?size=101", adminToken())).hasStatus(HttpStatus.BAD_REQUEST);
    }

    private JsonNode scroll(String token, String categoryId, String cursor, int size) {
        String uri = "/api/courses/scroll?categoryId=" + categoryId + "&size=" + size
                + (cursor == null ? "" : "&cursor=" + cursor);
        var result = get(uri, token);
        assertThat(result).hasStatus(HttpStatus.OK);
        return body(result);
    }

    private String createCourseIn(String categoryId, Account instructor) {
        return id(post("/api/courses", instructor.token(), """
                {"title": "Course %s", "durationHours": 10, "level": "BEGINNER", "price": 10,
                 "capacity": 5, "categoryId": "%s", "instructorId": "%s"}"""
                .formatted(unique(), categoryId, instructor.id())));
    }

    private String createPublishedCourseIn(String categoryId, Account instructor) {
        String courseId = createCourseIn(categoryId, instructor);
        assertThat(post("/api/courses/" + courseId + "/publish", instructor.token(), null)).hasStatus(HttpStatus.OK);
        return courseId;
    }

    private static List<String> ids(JsonNode page) {
        List<String> ids = new ArrayList<>();
        page.get("content").forEach(course -> ids.add(course.get("id").asString()));
        return ids;
    }
}
