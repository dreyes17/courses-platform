package com.example.courses.catalog.domain;

import java.util.UUID;

public class CourseFullException extends RuntimeException {

    public CourseFullException(UUID courseId) {
        super("Course %s has no available seats".formatted(courseId));
    }
}
