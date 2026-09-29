package com.example.courses.enrollment.application;


import java.time.Instant;
import java.util.UUID;

public record StudentView(UUID id, String firstName, String lastName, String email, Instant createdAt) {
}
