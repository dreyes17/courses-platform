package com.example.courses.enrollment.application;

import com.example.courses.enrollment.domain.Enrollment;
import com.example.courses.enrollment.domain.EnrollmentStatus;

import java.time.Instant;
import java.util.UUID;

/** A course a given student is enrolled in. */
public record StudentEnrollmentView(
        UUID enrollmentId,
        UUID courseId,
        String courseTitle,
        EnrollmentStatus status,
        int progress,
        Instant enrolledAt,
        Instant completedAt
) {

    static StudentEnrollmentView from(Enrollment enrollment) {
        var course = enrollment.getCourse();
        return new StudentEnrollmentView(enrollment.getId(), course.getId(), course.getTitle(),
                enrollment.getStatus(), enrollment.getProgress(), enrollment.getEnrolledAt(),
                enrollment.getCompletedAt());
    }
}
