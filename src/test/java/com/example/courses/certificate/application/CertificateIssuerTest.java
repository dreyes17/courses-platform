package com.example.courses.certificate.application;

import com.example.courses.certificate.domain.Certificate;
import com.example.courses.certificate.repository.CertificateRepository;
import com.example.courses.enrollment.domain.Enrollment;
import com.example.courses.enrollment.repository.EnrollmentRepository;
import com.example.courses.messaging.events.EnrollmentCompleted;
import com.example.courses.messaging.inbox.IdempotentConsumer;
import com.example.courses.shared.observability.BusinessMetrics;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static com.example.courses.support.DomainFixtures.completedEnrollment;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CertificateIssuerTest {

    private static final UUID EVENT_ID = UUID.randomUUID();

    @Mock
    private CertificateRepository certificates;
    @Mock
    private EnrollmentRepository enrollments;
    @Mock
    private IdempotentConsumer idempotentConsumer;
    @Mock
    private BusinessMetrics metrics;
    @InjectMocks
    private CertificateIssuer issuer;

    @Test
    void issuesACertificateWithAVerifiableCodeForACompletedEnrollment() {
        Enrollment enrollment = completedEnrollment();
        when(idempotentConsumer.isFirstDelivery(EVENT_ID, CertificateIssuer.CONSUMER)).thenReturn(true);
        when(certificates.existsByEnrollmentId(enrollment.getId())).thenReturn(false);
        when(enrollments.findById(enrollment.getId())).thenReturn(Optional.of(enrollment));
        when(certificates.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        issuer.onEnrollmentCompleted(EVENT_ID, completed(enrollment));

        var certificate = ArgumentCaptor.forClass(Certificate.class);
        verify(certificates).save(certificate.capture());
        assertThat(certificate.getValue().getEnrollment()).isSameAs(enrollment);
        assertThat(certificate.getValue().getCode()).matches("CERT-[0-9A-F]{16}");
        verify(metrics).certificateIssued();
    }

    @Test
    void doesNotIssueASecondCertificateForTheSameEnrollment() {
        Enrollment enrollment = completedEnrollment();
        when(idempotentConsumer.isFirstDelivery(EVENT_ID, CertificateIssuer.CONSUMER)).thenReturn(true);
        when(certificates.existsByEnrollmentId(enrollment.getId())).thenReturn(true);

        issuer.onEnrollmentCompleted(EVENT_ID, completed(enrollment));

        verify(certificates, never()).save(any());
        verifyNoInteractions(metrics);
    }

    @Test
    void duplicateDeliveryIsSkipped() {
        when(idempotentConsumer.isFirstDelivery(EVENT_ID, CertificateIssuer.CONSUMER)).thenReturn(false);

        issuer.onEnrollmentCompleted(EVENT_ID, completed(completedEnrollment()));

        verifyNoInteractions(certificates, enrollments);
    }

    private static EnrollmentCompleted completed(Enrollment enrollment) {
        return new EnrollmentCompleted(enrollment.getId(), enrollment.getStudent().getId(),
                enrollment.getCourse().getId(), Instant.now());
    }
}
