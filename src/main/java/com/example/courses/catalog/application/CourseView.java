package com.example.courses.catalog.application;

import com.example.courses.catalog.domain.Course;
import com.example.courses.catalog.domain.CourseLevel;
import com.example.courses.catalog.domain.CourseStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CourseView(
        UUID id,
        String title,
        String description,
        int durationHours,
        CourseLevel level,
        BigDecimal price,
        int capacity,
        int seatsTaken,
        int availableSeats,
        CourseStatus status,
        UUID categoryId,
        String categoryName,
        UUID instructorId,
        String instructorName,
        Instant createdAt
) {

    static CourseView from(Course course) {
        return new CourseView(course.getId(), course.getTitle(), course.getDescription(), course.getDurationHours(),
                course.getLevel(), course.getPrice(), course.getCapacity(), course.getSeatsTaken(),
                course.getCapacity() - course.getSeatsTaken(), course.getStatus(),
                course.getCategory().getId(), course.getCategory().getName(),
                course.getInstructor().getId(), course.getInstructor().getName(), course.getCreatedAt());
    }
}
