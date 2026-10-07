package io.github.dreyes17.courses.certificate.application;

import io.github.dreyes17.courses.certificate.domain.Certificate;
import io.github.dreyes17.courses.certificate.repository.CertificateRepository;
import io.github.dreyes17.courses.enrollment.domain.Enrollment;
import io.github.dreyes17.courses.enrollment.repository.EnrollmentRepository;
import io.github.dreyes17.courses.messaging.events.EnrollmentCompleted;
import io.github.dreyes17.courses.messaging.inbox.IdempotentConsumer;
import io.github.dreyes17.courses.shared.application.ResourceNotFoundException;
import io.github.dreyes17.courses.shared.observability.BusinessMetrics;
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
