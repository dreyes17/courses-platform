package com.example.courses.enrollment.repository;

import com.example.courses.enrollment.domain.Student;
import com.example.courses.shared.repository.SpecificationFilters;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface StudentRepository extends JpaRepository<Student, UUID>, JpaSpecificationExecutor<Student> {

    /** Null filters are ignored. The name matches either the first or the last name. */
    static Specification<Student> matching(String nameContains, String emailContains) {
        return SpecificationFilters.allOf(
                SpecificationFilters.containsIgnoringCase(nameContains, "firstName", "lastName"),
                SpecificationFilters.containsIgnoringCase(emailContains, "email"));
    }

    Optional<Student> findByEmail(String email);

    boolean existsByEmail(String email);
}
