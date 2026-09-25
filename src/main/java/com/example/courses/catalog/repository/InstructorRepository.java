package com.example.courses.catalog.repository;

import com.example.courses.catalog.domain.Instructor;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface InstructorRepository extends JpaRepository<Instructor, UUID> {

    Optional<Instructor> findByEmail(String email);

    boolean existsByEmail(String email);
}
