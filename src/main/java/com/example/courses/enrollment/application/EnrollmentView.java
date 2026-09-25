package com.example.courses.enrollment.application;

import com.example.courses.enrollment.domain.Enrollment;
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

    static EnrollmentView from(Enrollment enrollment) {
        return new EnrollmentView(
                enrollment.getId(),
                enrollment.getStudent().getId(),
                enrollment.getCourse().getId(),
                enrollment.getStatus(),
                enrollment.getProgress(),
                enrollment.getEnrolledAt(),
                enrollment.getCompletedAt());
    }
}
