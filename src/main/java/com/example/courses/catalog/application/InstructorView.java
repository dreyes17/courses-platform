package com.example.courses.catalog.application;

import com.example.courses.catalog.domain.Instructor;

import java.time.Instant;
import java.util.UUID;

public record InstructorView(UUID id, String name, String email, String bio, Instant createdAt) {

    static InstructorView from(Instructor instructor) {
        return new InstructorView(instructor.getId(), instructor.getName(), instructor.getEmail(),
                instructor.getBio(), instructor.getCreatedAt());
    }
}
