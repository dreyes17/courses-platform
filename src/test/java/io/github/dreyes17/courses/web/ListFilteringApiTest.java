package io.github.dreyes17.courses.web;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/** The optional filters of the list endpoints, alone and combined, on top of pagination. */
class ListFilteringApiTest extends ApiTestSupport {

    @Test
    void categoriesFilterByNameAndStatus() {
        String admin = adminToken();
        String token = unique();
        post("/api/categories", admin, """
                {"name": "Backend %s"}""".formatted(token));
        String archived = id(post("/api/categories", admin, """
                {"name": "Frontend %s"}""".formatted(token)));
        post("/api/categories/" + archived + "/archive", admin, null);
        // The unfiltered list is cached; a filtered one must never be answered from that cache.
        get("/api/categories", admin);

        assertThat(get("/api/categories?name=" + token.toUpperCase(), admin))
                .bodyJson().extractingPath("$.totalElements").isEqualTo(2);
        assertThat(get("/api/categories?name=" + token + "&status=ARCHIVED", admin))
                .bodyJson().extractingPath("$.content[0].id").isEqualTo(archived);
        assertThat(get("/api/categories?name=" + token + "&status=ARCHIVED", admin))
                .bodyJson().extractingPath("$.totalElements").isEqualTo(1);
    }

    @Test
    void instructorsFilterByEmail() {
        Account instructor = createInstructor();
        createInstructor();

        assertThat(get("/api/instructors?email=" + instructor.email(), adminToken()))
                .bodyJson().extractingPath("$.content[0].id").isEqualTo(instructor.id());
        assertThat(get("/api/instructors?email=" + instructor.email(), adminToken()))
                .bodyJson().extractingPath("$.totalElements").isEqualTo(1);
    }

    @Test
    void studentsFilterByNameAndEmail() {
        Account student = registerStudent();
        String admin = adminToken();

        assertThat(get("/api/students?name=LOVELACE&email=" + student.email(), admin))
                .bodyJson().extractingPath("$.totalElements").isEqualTo(1);
        assertThat(get("/api/students?name=turing&email=" + student.email(), admin))
                .bodyJson().extractingPath("$.totalElements").isEqualTo(0);
    }

    @Test
    void enrollmentsOfACourseAndOfAStudentFilterByStatus() {
        Account instructor = createInstructor();
        String courseId = createPublishedCourse(instructor, 5, new BigDecimal("10.00"));
        Account staying = registerStudent();
        Account leaving = registerStudent();
        String stayingEnrollment = id(enroll(staying.token(), courseId, UUID.randomUUID().toString()));
        String leavingEnrollment = id(enroll(leaving.token(), courseId, UUID.randomUUID().toString()));
        // Wait for the asynchronous activation, so the statuses below no longer change.
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            assertThat(get("/api/enrollments/" + stayingEnrollment, staying.token()))
                    .bodyJson().extractingPath("$.status").isEqualTo("ACTIVE");
            assertThat(get("/api/enrollments/" + leavingEnrollment, leaving.token()))
                    .bodyJson().extractingPath("$.status").isEqualTo("ACTIVE");
        });
        post("/api/enrollments/" + leavingEnrollment + "/cancel", leaving.token(), null);

        String ofCourse = "/api/courses/" + courseId + "/enrollments";
        assertThat(get(ofCourse, instructor.token())).bodyJson().extractingPath("$.totalElements").isEqualTo(2);
        assertThat(get(ofCourse + "?status=ACTIVE", instructor.token()))
                .bodyJson().extractingPath("$.content[0].enrollmentId").isEqualTo(stayingEnrollment);
        assertThat(get(ofCourse + "?status=CANCELLED", instructor.token()))
                .bodyJson().extractingPath("$.content[0].enrollmentId").isEqualTo(leavingEnrollment);
        assertThat(get("/api/students/" + leaving.id() + "/enrollments?status=ACTIVE", leaving.token()))
                .bodyJson().extractingPath("$.totalElements").isEqualTo(0);
    }

    @Test
    void adminListsEveryEnrollmentFilteredByCourseStudentAndStatus() {
        Account instructor = createInstructor();
        String courseId = createPublishedCourse(instructor, 5, new BigDecimal("10.00"));
        String otherCourseId = createPublishedCourse(instructor, 5, new BigDecimal("10.00"));
        Account student = registerStudent();
        String inCourse = id(enroll(student.token(), courseId, UUID.randomUUID().toString()));
        String inOtherCourse = id(enroll(student.token(), otherCourseId, UUID.randomUUID().toString()));
        post("/api/enrollments/" + inOtherCourse + "/cancel", student.token(), null);
        String admin = adminToken();

        assertThat(get("/api/enrollments?studentId=" + student.id(), admin))
                .bodyJson().extractingPath("$.totalElements").isEqualTo(2);
        var ofCourse = get("/api/enrollments?courseId=" + courseId + "&studentId=" + student.id(), admin);
        assertThat(ofCourse).bodyJson().extractingPath("$.content[0].enrollmentId").isEqualTo(inCourse);
        assertThat(ofCourse).bodyJson().extractingPath("$.content[0].studentEmail").isEqualTo(student.email());
        assertThat(ofCourse).bodyJson().extractingPath("$.content[0].courseTitle").asString().startsWith("Course ");
        assertThat(get("/api/enrollments?studentId=" + student.id() + "&status=CANCELLED", admin))
                .bodyJson().extractingPath("$.content[0].enrollmentId").isEqualTo(inOtherCourse);
        assertThat(get("/api/enrollments?studentId=" + student.id() + "&status=CANCELLED", admin))
                .bodyJson().extractingPath("$.totalElements").isEqualTo(1);
    }

    @Test
    void onlyAdminsListEveryEnrollment() {
        assertThat(get("/api/enrollments", registerStudent().token())).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(get("/api/enrollments", createInstructor().token())).hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void unknownFilterValueReturns400() {
        assertThat(get("/api/categories?status=DELETED", adminToken())).hasStatus(HttpStatus.BAD_REQUEST);
    }
}
