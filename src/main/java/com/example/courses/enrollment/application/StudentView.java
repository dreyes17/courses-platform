package com.example.courses.enrollment.application;

import com.example.courses.enrollment.domain.Student;

import java.time.Instant;
import java.util.UUID;

public record StudentView(UUID id, String firstName, String lastName, String email, Instant createdAt) {

    static StudentView from(Student student) {
        return new StudentView(student.getId(), student.getFirstName(), student.getLastName(), student.getEmail(),
                student.getCreatedAt());
    }
}
