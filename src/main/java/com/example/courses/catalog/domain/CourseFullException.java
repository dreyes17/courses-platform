package com.example.courses.catalog.domain;

import com.example.courses.shared.domain.ConflictException;

import java.util.UUID;

public class CourseFullException extends ConflictException {

    public CourseFullException(UUID courseId) {
        super("Course %s has no available seats".formatted(courseId));
    }
}
