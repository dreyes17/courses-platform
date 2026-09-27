package com.example.courses.certificate.application;

import com.example.courses.certificate.domain.Certificate;
import com.example.courses.certificate.repository.CertificateRepository;
import com.example.courses.enrollment.domain.Enrollment;
import com.example.courses.enrollment.repository.EnrollmentRepository;
import com.example.courses.messaging.events.EnrollmentCompleted;
import com.example.courses.messaging.inbox.IdempotentConsumer;
import com.example.courses.shared.application.ResourceNotFoundException;
import com.example.courses.shared.observability.BusinessMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class CertificateIssuer {

    static final String CONSUMER = "certificate-issuer";

    private static final Logger log = LoggerFactory.getLogger(CertificateIssuer.class);

    private final CertificateRepository certificates;
    private final EnrollmentRepository enrollments;
    private final IdempotentConsumer idempotentConsumer;
    private final BusinessMetrics metrics;

    public CertificateIssuer(CertificateRepository certificates, EnrollmentRepository enrollments,
                             IdempotentConsumer idempotentConsumer, BusinessMetrics metrics) {
        this.certificates = certificates;
        this.enrollments = enrollments;
        this.idempotentConsumer = idempotentConsumer;
        this.metrics = metrics;
    }

    @Transactional
    public void onEnrollmentCompleted(UUID eventId, EnrollmentCompleted event) {
        if (!idempotentConsumer.isFirstDelivery(eventId, CONSUMER)) {
            log.debug("Skipping duplicate EnrollmentCompleted {}", eventId);
            return;
        }
        if (certificates.existsByEnrollmentId(event.enrollmentId())) {
            return;
        }
        Enrollment enrollment = enrollments.findById(event.enrollmentId())
                .orElseThrow(() -> new ResourceNotFoundException("Enrollment", event.enrollmentId()));
        Certificate certificate = certificates.save(Certificate.issueFor(enrollment));
        metrics.certificateIssued();
        log.info("Issued certificate {} for enrollment {}", certificate.getCode(), enrollment.getId());
    }
}
