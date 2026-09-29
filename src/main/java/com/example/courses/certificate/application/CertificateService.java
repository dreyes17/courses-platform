package com.example.courses.certificate.application;

import com.example.courses.certificate.repository.CertificateRepository;
import com.example.courses.enrollment.repository.EnrollmentRepository;
import com.example.courses.shared.application.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Read side of certificates. They are issued asynchronously by {@link CertificateIssuer}. */
@Service
public class CertificateService {

    private final CertificateRepository certificates;
    private final EnrollmentRepository enrollments;
    private final CertificateViewMapper mapper;

    CertificateService(CertificateRepository certificates, EnrollmentRepository enrollments,
                       CertificateViewMapper mapper) {
        this.certificates = certificates;
        this.enrollments = enrollments;
        this.mapper = mapper;
    }

    /**
     * 404 names what is missing: the enrollment, or its certificate, which only exists once the enrollment is
     * COMPLETED and the EnrollmentCompleted event has been processed.
     */
    @Transactional(readOnly = true)
    public CertificateView getForEnrollment(UUID enrollmentId) {
        return certificates.findWithDetailsByEnrollmentId(enrollmentId)
                .map(mapper::toView)
                .orElseThrow(() -> enrollments.existsById(enrollmentId)
                        ? new ResourceNotFoundException("Certificate of enrollment", enrollmentId)
                        : new ResourceNotFoundException("Enrollment", enrollmentId));
    }

    @Transactional(readOnly = true)
    public CertificateVerification verify(String code) {
        return certificates.findWithDetailsByCode(code)
                .map(mapper::toVerification)
                .orElseThrow(() -> new ResourceNotFoundException("Certificate", code));
    }
}
