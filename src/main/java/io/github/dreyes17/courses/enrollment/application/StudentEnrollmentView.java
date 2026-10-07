package io.github.dreyes17.courses.enrollment.application;

import io.github.dreyes17.courses.enrollment.domain.EnrollmentStatus;

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
}
