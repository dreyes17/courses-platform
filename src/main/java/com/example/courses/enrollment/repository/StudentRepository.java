package com.example.courses.enrollment.repository;

import com.example.courses.enrollment.domain.Student;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface StudentRepository extends JpaRepository<Student, UUID> {

    Optional<Student> findByEmail(String email);

    boolean existsByEmail(String email);
}
