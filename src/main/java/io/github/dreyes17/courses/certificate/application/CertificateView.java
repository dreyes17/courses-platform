package io.github.dreyes17.courses.certificate.application;

import java.time.Instant;
import java.util.UUID;

/** The certificate of a completed enrollment, as its student, the course's instructor or an ADMIN see it. */
public record CertificateView(
        String code,
        UUID enrollmentId,
        UUID studentId,
        UUID courseId,
        String courseTitle,
        Instant issuedAt
) {
}
