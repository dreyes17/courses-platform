package io.github.dreyes17.courses.catalog.domain;

import io.github.dreyes17.courses.shared.domain.ConflictException;

import java.util.UUID;

public class CourseFullException extends ConflictException {

    public CourseFullException(UUID courseId) {
        super("Course %s has no available seats".formatted(courseId));
    }
}
