package com.example.courses.web;

import com.example.courses.AbstractIntegrationTest;
import com.example.courses.catalog.application.CourseSearchCriteria;
import com.example.courses.catalog.application.CourseService;
import com.example.courses.catalog.domain.CourseLevel;
import com.example.courses.enrollment.application.EnrollmentService;
import com.example.courses.support.SqlStatementCounter;
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

    @Test
    void studentsOfACourseLoadWithoutNPlusOne() {
        UUID courseId = publishedCourse(ROWS, BigDecimal.TEN);
        for (int i = 0; i < ROWS; i++) {
            enrollmentService.enroll(student(), courseId, null);
        }

        SqlStatementCounter.reset();
        var page = enrollmentService.listStudentsOfCourse(courseId, PageRequest.of(0, 20));

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
        var page = enrollmentService.listCoursesOfStudent(studentId, PageRequest.of(0, 20));

        assertThat(page.getContent()).hasSize(ROWS).allSatisfy(row -> assertThat(row.courseTitle()).isNotBlank());
        assertThat(SqlStatementCounter.statements())
                .hasSizeLessThanOrEqualTo(2)
                .anySatisfy(sql -> assertThat(sql).containsIgnoringCase("join courses"));
    }

    @Test
    void courseSearchLoadsCategoryAndInstructorInTheSameQuery() {
        for (int i = 0; i < ROWS; i++) {
            publishedCourse(5, new BigDecimal("30.00"));
        }
        var criteria = new CourseSearchCriteria(null, CourseLevel.BEGINNER, new BigDecimal("25"),
                new BigDecimal("35"), "course", true, null);

        SqlStatementCounter.reset();
        var page = courseService.search(criteria, PageRequest.of(0, ROWS, Sort.by("createdAt")));

        assertThat(page.getContent()).hasSize(ROWS)
                .allSatisfy(course -> assertThat(course.categoryName()).isNotBlank())
                .allSatisfy(course -> assertThat(course.instructorName()).isNotBlank());
        assertThat(SqlStatementCounter.statements())
                .as("one page query with joins + one count query")
                .hasSizeLessThanOrEqualTo(2)
                .anySatisfy(sql -> assertThat(sql).containsIgnoringCase("join instructors"));
    }
}
