package com.example.courses.enrollment.domain;

import java.util.UUID;

public class InvalidEnrollmentStateException extends RuntimeException {

    public InvalidEnrollmentStateException(UUID enrollmentId, EnrollmentStatus current, String attemptedAction) {
        super("Enrollment %s cannot %s while in status %s".formatted(enrollmentId, attemptedAction, current));
    }
}
