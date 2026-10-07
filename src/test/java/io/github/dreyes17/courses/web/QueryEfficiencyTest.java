package io.github.dreyes17.courses.web;

import io.github.dreyes17.courses.AbstractIntegrationTest;
import io.github.dreyes17.courses.catalog.application.CourseSearchCriteria;
import io.github.dreyes17.courses.catalog.application.CourseService;
import io.github.dreyes17.courses.catalog.application.CourseVisibility;
import io.github.dreyes17.courses.catalog.domain.Course;
import io.github.dreyes17.courses.catalog.domain.CourseLevel;
import io.github.dreyes17.courses.enrollment.application.EnrollmentService;
import io.github.dreyes17.courses.enrollment.domain.Enrollment;
import io.github.dreyes17.courses.enrollment.domain.EnrollmentStatus;
import io.github.dreyes17.courses.enrollment.repository.EnrollmentRepository;
import io.github.dreyes17.courses.support.SqlStatementCounter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Relational listings must load a whole page in a constant number of queries, whatever the page size. */
class QueryEfficiencyTest extends AbstractIntegrationTest {

    private static final int ROWS = 6;

    @Autowired
    private EnrollmentService enrollmentService;
    @Autowired
    private CourseService courseService;
    @Autowired
    private EnrollmentRepository enrollments;

    @Test
    void studentsOfACourseLoadWithoutNPlusOne() {
        UUID courseId = publishedCourse(ROWS, BigDecimal.TEN);
        for (int i = 0; i < ROWS; i++) {
            enrollmentService.enroll(student(), courseId, null);
        }

        SqlStatementCounter.reset();
        var page = enrollmentService.listStudentsOfCourse(courseId, null, PageRequest.of(0, 20));

        assertThat(page.getContent()).hasSize(ROWS).allSatisfy(row -> assertThat(row.email()).isNotBlank());
        assertThat(SqlStatementCounter.statements())
                .as("existence check + one joined page query, never one query per student")
                .hasSizeLessThanOrEqualTo(2)
                .anySatisfy(sql -> assertThat(sql).containsIgnoringCase("join students"));
    }

    @Test
    void coursesOfAStudentLoadWithoutNPlusOne() {
        UUID studentId = student();
        for (int i = 0; i < ROWS; i++) {
            enrollmentService.enroll(studentId, publishedCourse(5, BigDecimal.TEN), null);
        }

        SqlStatementCounter.reset();
        var page = enrollmentService.listCoursesOfStudent(studentId, null, PageRequest.of(0, 20));

        assertThat(page.getContent()).hasSize(ROWS).allSatisfy(row -> assertThat(row.courseTitle()).isNotBlank());
        assertThat(SqlStatementCounter.statements())
                .hasSizeLessThanOrEqualTo(2)
                .anySatisfy(sql -> assertThat(sql).containsIgnoringCase("join courses"));
    }

    @Test
    void studentsOfACourseFilteredByStatusLoadWithoutNPlusOne() {
        UUID courseId = publishedCourse(ROWS, BigDecimal.TEN);
        // Built through the domain, bypassing the outbox, so no payment event changes a status mid-test.
        transactionTemplate.executeWithoutResult(status -> {
            Course course = courses.findById(courseId).orElseThrow();
            for (int i = 0; i < ROWS; i++) {
                Enrollment enrollment = Enrollment.requestFor(students.findById(student()).orElseThrow(), course);
                if (i % 2 == 0) {
                    enrollment.activate();
                }
                enrollments.save(enrollment);
            }
        });

        SqlStatementCounter.reset();
        var page = enrollmentService.listStudentsOfCourse(courseId, EnrollmentStatus.ACTIVE, PageRequest.of(0, 20));

        assertThat(page.getContent()).hasSize(ROWS / 2)
                .allSatisfy(row -> assertThat(row.status()).isEqualTo(EnrollmentStatus.ACTIVE))
                .allSatisfy(row -> assertThat(row.email()).isNotBlank());
        assertThat(SqlStatementCounter.statements())
                .hasSizeLessThanOrEqualTo(2)
                .anySatisfy(sql -> assertThat(sql).containsIgnoringCase("join students"));
    }

    @Test
    void everyEnrollmentLoadsStudentAndCourseInTheSameQuery() {
        UUID courseId = publishedCourse(ROWS, BigDecimal.TEN);
        for (int i = 0; i < ROWS; i++) {
            enrollmentService.enroll(student(), courseId, null);
        }

        SqlStatementCounter.reset();
        var page = enrollmentService.list(courseId, null, null, PageRequest.of(0, 20, Sort.by("enrolledAt")));

        assertThat(page.getContent()).hasSize(ROWS)
                .allSatisfy(row -> assertThat(row.studentEmail()).isNotBlank())
                .allSatisfy(row -> assertThat(row.courseTitle()).isNotBlank());
        assertThat(SqlStatementCounter.statements())
                .as("one page query joining students and courses + at most one count query")
                .hasSizeLessThanOrEqualTo(2)
                .anySatisfy(sql -> assertThat(sql).containsIgnoringCase("join students")
                        .containsIgnoringCase("join courses"));
    }

    @Test
    void courseSearchLoadsCategoryAndInstructorInTheSameQuery() {
        for (int i = 0; i < ROWS; i++) {
            publishedCourse(5, new BigDecimal("30.00"));
        }
        var criteria = new CourseSearchCriteria(null, CourseLevel.BEGINNER, new BigDecimal("25"),
                new BigDecimal("35"), "course", true, null);

        SqlStatementCounter.reset();
        var page = courseService.search(criteria, PageRequest.of(0, ROWS, Sort.by("createdAt")),
                CourseVisibility.ALL);

        assertThat(page.getContent()).hasSize(ROWS)
                .allSatisfy(course -> assertThat(course.categoryName()).isNotBlank())
                .allSatisfy(course -> assertThat(course.instructorName()).isNotBlank());
        assertThat(SqlStatementCounter.statements())
                .as("one page query with joins + one count query")
                .hasSizeLessThanOrEqualTo(2)
                .anySatisfy(sql -> assertThat(sql).containsIgnoringCase("join instructors"));
    }

    @Test
    void courseScrollLoadsAPageInASingleQuery() {
        for (int i = 0; i < ROWS; i++) {
            publishedCourse(5, new BigDecimal("30.00"));
        }
        var criteria = new CourseSearchCriteria(null, null, null, null, null, null, null);

        SqlStatementCounter.reset();
        var page = courseService.scroll(criteria, null, ROWS, CourseVisibility.ALL);

        assertThat(page.content()).hasSize(ROWS)
                .allSatisfy(course -> assertThat(course.categoryName()).isNotBlank())
                .allSatisfy(course -> assertThat(course.instructorName()).isNotBlank());
        assertThat(SqlStatementCounter.statements())
                .as("one query with joins and no count query: size + 1 rows tell whether there is a next page")
                .hasSize(1)
                .allSatisfy(sql -> assertThat(sql).containsIgnoringCase("join instructors")
                        .as("rows limited by the database, not in memory").containsPattern("(?i)fetch first|limit"));
    }
}
