package io.github.dreyes17.courses.catalog.application;

import io.github.dreyes17.courses.catalog.domain.CourseLevel;

import java.math.BigDecimal;

/** The editable part of a course, shared by create and update. */
public record CourseTerms(String title, String description, int durationHours, CourseLevel level, BigDecimal price,
                          int capacity) {
}
