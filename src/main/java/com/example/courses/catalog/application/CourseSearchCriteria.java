package com.example.courses.catalog.application;

import com.example.courses.catalog.domain.CourseLevel;
import com.example.courses.catalog.domain.CourseStatus;

import java.math.BigDecimal;
import java.util.UUID;

/** Every field is optional; null means "don't filter on this". */
public record CourseSearchCriteria(UUID categoryId, CourseLevel level, BigDecimal minPrice, BigDecimal maxPrice,
                                   String title, Boolean withAvailableSeats, CourseStatus status) {
}
