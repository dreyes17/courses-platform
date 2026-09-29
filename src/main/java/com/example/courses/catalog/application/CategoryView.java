package com.example.courses.catalog.application;

import com.example.courses.catalog.domain.CategoryStatus;

import java.time.Instant;
import java.util.UUID;

public record CategoryView(UUID id, String name, String description, CategoryStatus status, Instant createdAt) {
}
