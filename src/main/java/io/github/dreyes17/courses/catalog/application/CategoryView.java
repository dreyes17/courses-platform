package io.github.dreyes17.courses.catalog.application;

import io.github.dreyes17.courses.catalog.domain.CategoryStatus;

import java.time.Instant;
import java.util.UUID;

public record CategoryView(UUID id, String name, String description, CategoryStatus status, Instant createdAt) {
}
