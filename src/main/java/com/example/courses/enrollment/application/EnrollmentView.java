package com.example.courses.enrollment.application;

import com.example.courses.enrollment.domain.EnrollmentStatus;

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
