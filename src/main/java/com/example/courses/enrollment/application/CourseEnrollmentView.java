package com.example.courses.enrollment.application;

import com.example.courses.enrollment.domain.Enrollment;
import com.example.courses.enrollment.domain.EnrollmentStatus;

import java.time.Instant;
import java.util.UUID;

/** A student enrolled in a given course. */
public record CourseEnrollmentView(
        UUID enrollmentId,
        UUID studentId,
        String firstName,
        String lastName,
        String email,
        EnrollmentStatus status,
        int progress,
        Instant enrolledAt
) {

    static CourseEnrollmentView from(Enrollment enrollment) {
        var student = enrollment.getStudent();
        return new CourseEnrollmentView(enrollment.getId(), student.getId(), student.getFirstName(),
                student.getLastName(), student.getEmail(), enrollment.getStatus(), enrollment.getProgress(),
                enrollment.getEnrolledAt());
    }
}
