package com.example.courses.support;

import com.example.courses.catalog.domain.Category;
import com.example.courses.catalog.domain.Course;
import com.example.courses.catalog.domain.CourseLevel;
import com.example.courses.catalog.domain.Instructor;
import com.example.courses.enrollment.domain.Enrollment;
import com.example.courses.enrollment.domain.Student;
import com.example.courses.payment.domain.Payment;
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
