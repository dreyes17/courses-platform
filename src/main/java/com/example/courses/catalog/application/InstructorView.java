package com.example.courses.catalog.application;


import java.time.Instant;
import java.util.UUID;

public record InstructorView(UUID id, String name, String email, String bio, Instant createdAt) {
}
