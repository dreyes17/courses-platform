package io.github.dreyes17.courses.enrollment.application;

import io.github.dreyes17.courses.enrollment.domain.Enrollment;
import org.junit.jupiter.api.Test;

import static io.github.dreyes17.courses.support.DomainFixtures.completedEnrollment;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The compiler already rejects a view field with no source (unmappedTargetPolicy = ERROR); these tests cover
 * what it can't see: that each id and name comes from the right side of the enrollment.
 */
class EnrollmentViewMapperTest {

    private final EnrollmentViewMapper mapper = new EnrollmentViewMapperImpl();
    private final Enrollment enrollment = completedEnrollment();

    @Test
    void enrollmentViewReferencesStudentAndCourse() {
        EnrollmentView view = mapper.toView(enrollment);

        assertThat(view.id()).isEqualTo(enrollment.getId());
        assertThat(view.studentId()).isEqualTo(enrollment.getStudent().getId());
        assertThat(view.courseId()).isEqualTo(enrollment.getCourse().getId());
        assertThat(view.progress()).isEqualTo(100);
        assertThat(view.completedAt()).isEqualTo(enrollment.getCompletedAt()).isNotNull();
    }

    @Test
    void courseEnrollmentViewDescribesTheStudent() {
        CourseEnrollmentView view = mapper.toCourseEnrollmentView(enrollment);

        assertThat(view.enrollmentId()).isEqualTo(enrollment.getId());
        assertThat(view.studentId()).isEqualTo(enrollment.getStudent().getId());
        assertThat(view.firstName()).isEqualTo("Ada");
        assertThat(view.lastName()).isEqualTo("Lovelace");
        assertThat(view.email()).isEqualTo(enrollment.getStudent().getEmail());
    }

    @Test
    void studentEnrollmentViewDescribesTheCourse() {
        StudentEnrollmentView view = mapper.toStudentEnrollmentView(enrollment);

        assertThat(view.enrollmentId()).isEqualTo(enrollment.getId());
        assertThat(view.courseId()).isEqualTo(enrollment.getCourse().getId());
        assertThat(view.courseTitle()).isEqualTo(enrollment.getCourse().getTitle());
        assertThat(view.status()).isEqualTo(enrollment.getStatus());
    }
}
