package com.example.courses.enrollment.application;

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
}
