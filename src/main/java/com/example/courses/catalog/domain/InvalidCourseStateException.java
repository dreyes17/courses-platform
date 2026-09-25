package com.example.courses.catalog.domain;

import java.util.UUID;

public class InvalidCourseStateException extends RuntimeException {

    public InvalidCourseStateException(UUID courseId, CourseStatus current, String attemptedAction) {
        super("Course %s cannot %s while in status %s".formatted(courseId, attemptedAction, current));
    }
}
