package com.example.courses.catalog.application;

import com.example.courses.catalog.domain.CourseLevel;

import java.math.BigDecimal;

/** The editable part of a course, shared by create and update. */
public record CourseTerms(String title, String description, int durationHours, CourseLevel level, BigDecimal price,
                          int capacity) {
}
