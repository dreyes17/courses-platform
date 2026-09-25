package com.example.courses.certificate.domain;

import com.example.courses.enrollment.domain.Enrollment;
import com.example.courses.enrollment.domain.EnrollmentStatus;
import com.example.courses.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "certificates")
public class Certificate extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "enrollment_id", nullable = false, unique = true)
    private Enrollment enrollment;

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    protected Certificate() {
    }

    private Certificate(Enrollment enrollment, String code) {
        this.enrollment = Objects.requireNonNull(enrollment, "enrollment");
        this.code = Objects.requireNonNull(code, "code");
        this.issuedAt = Instant.now();
    }

    public static Certificate issueFor(Enrollment enrollment) {
        if (enrollment.getStatus() != EnrollmentStatus.COMPLETED) {
            throw new IllegalStateException(
                    "Cannot issue a certificate for enrollment %s in status %s"
                            .formatted(enrollment.getId(), enrollment.getStatus()));
        }
        return new Certificate(enrollment, generateCode());
    }

    private static String generateCode() {
        return "CERT-" + UUID.randomUUID().toString().toUpperCase().replace("-", "").substring(0, 16);
    }

    public Enrollment getEnrollment() {
        return enrollment;
    }

    public String getCode() {
        return code;
    }

    public Instant getIssuedAt() {
        return issuedAt;
    }
}
