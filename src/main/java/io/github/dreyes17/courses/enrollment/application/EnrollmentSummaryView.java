package io.github.dreyes17.courses.enrollment.application;

import io.github.dreyes17.courses.enrollment.domain.EnrollmentStatus;

import java.time.Instant;
import java.util.UUID;

/** An enrollment in the platform-wide listing: who, in which course, and how far along. */
public record EnrollmentSummaryView(
        UUID enrollmentId,
        UUID studentId,
        String studentFirstName,
        String studentLastName,
        String studentEmail,
        UUID courseId,
        String courseTitle,
        EnrollmentStatus status,
        int progress,
        Instant enrolledAt,
        Instant completedAt
) {
}
