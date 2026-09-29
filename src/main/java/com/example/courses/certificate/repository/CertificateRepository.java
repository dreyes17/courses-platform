package com.example.courses.certificate.repository;

import com.example.courses.certificate.domain.Certificate;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CertificateRepository extends JpaRepository<Certificate, UUID> {

    Optional<Certificate> findByEnrollmentId(UUID enrollmentId);

    boolean existsByEnrollmentId(UUID enrollmentId);

    /** The student and course the views show, loaded in the same query. */
    @EntityGraph(attributePaths = {"enrollment.student", "enrollment.course"})
    Optional<Certificate> findWithDetailsByEnrollmentId(UUID enrollmentId);

    @EntityGraph(attributePaths = {"enrollment.student", "enrollment.course"})
    Optional<Certificate> findWithDetailsByCode(String code);
}
