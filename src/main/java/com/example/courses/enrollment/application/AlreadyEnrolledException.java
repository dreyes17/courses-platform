package com.example.courses.enrollment.application;

import java.util.UUID;

public class AlreadyEnrolledException extends RuntimeException {

    public AlreadyEnrolledException(UUID studentId, UUID courseId) {
        super("Student %s already has an active enrollment in course %s".formatted(studentId, courseId));
    }
}
