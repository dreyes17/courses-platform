package io.github.dreyes17.courses.enrollment.application;

import io.github.dreyes17.courses.enrollment.domain.EnrollmentStatus;

import java.time.Instant;
import java.util.UUID;

public record EnrollmentView(
        UUID id,
        UUID studentId,
        UUID courseId,
        EnrollmentStatus status,
        int progress,
        Instant enrolledAt,
        Instant completedAt
) {
}
