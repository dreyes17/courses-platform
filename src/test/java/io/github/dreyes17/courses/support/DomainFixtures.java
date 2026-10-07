package io.github.dreyes17.courses.support;

import io.github.dreyes17.courses.catalog.domain.Category;
import io.github.dreyes17.courses.catalog.domain.Course;
import io.github.dreyes17.courses.catalog.domain.CourseLevel;
import io.github.dreyes17.courses.catalog.domain.Instructor;
import io.github.dreyes17.courses.enrollment.domain.Enrollment;
import io.github.dreyes17.courses.enrollment.domain.Student;
import io.github.dreyes17.courses.payment.domain.Payment;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.UUID;

/** Entities for unit tests, with the id Hibernate would otherwise assign on persist. */
public final class DomainFixtures {

    private DomainFixtures() {
    }

    public static <T> T withId(T entity) {
        ReflectionTestUtils.setField(entity, "id", UUID.randomUUID());
        return entity;
    }

    public static Category category() {
        return withId(Category.create("Backend", null));
    }

    public static Instructor instructor() {
        return withId(Instructor.create("Grace Hopper", "grace@teach.test", null));
    }

    public static Course draftCourse(int capacity, BigDecimal price) {
        return withId(Course.draft("Spring", null, 10, CourseLevel.BEGINNER, price, capacity, category(),
                instructor()));
    }

    public static Course publishedCourse(int capacity, BigDecimal price) {
        Course course = draftCourse(capacity, price);
        course.publish();
        return course;
    }

    /** seatsTaken only changes through the atomic UPDATE in the database, so tests set it directly. */
    public static Course withSeatsTaken(Course course, int seatsTaken) {
        ReflectionTestUtils.setField(course, "seatsTaken", seatsTaken);
        return course;
    }

    public static Student student() {
        return withId(Student.register("Ada", "Lovelace", "ada@learn.test"));
    }

    public static Enrollment pendingEnrollment() {
        return withId(Enrollment.requestFor(student(), publishedCourse(10, BigDecimal.TEN)));
    }

    public static Enrollment activeEnrollment() {
        Enrollment enrollment = pendingEnrollment();
        enrollment.activate();
        return enrollment;
    }

    public static Enrollment completedEnrollment() {
        Enrollment enrollment = activeEnrollment();
        enrollment.updateProgress(100);
        return enrollment;
    }

    public static Payment pendingPayment(Enrollment enrollment) {
        return withId(Payment.requestFor(enrollment, new BigDecimal("49.90"), "EUR", "enrollment-" + enrollment.getId()));
    }
}
