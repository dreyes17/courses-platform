package io.github.dreyes17.courses.enrollment.application;

import io.github.dreyes17.courses.shared.domain.ConflictException;

import java.util.UUID;

public class AlreadyEnrolledException extends ConflictException {

    public AlreadyEnrolledException(UUID studentId, UUID courseId) {
        super("Student %s already has an active enrollment in course %s".formatted(studentId, courseId));
    }
}
