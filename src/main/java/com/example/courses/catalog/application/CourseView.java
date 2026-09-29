package com.example.courses.catalog.application;

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
}
