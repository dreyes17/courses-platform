package com.example.courses.payment.application;

import com.example.courses.payment.repository.PaymentRepository;
import com.example.courses.shared.application.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Read side of payments. They are created with the enrollment and settled by {@link PaymentProcessor}. */
@Service
public class PaymentService {

    private final PaymentRepository payments;
    private final PaymentViewMapper mapper;

    PaymentService(PaymentRepository payments, PaymentViewMapper mapper) {
        this.payments = payments;
        this.mapper = mapper;
    }

    /** Every enrollment is created with its payment, so a missing payment means the enrollment doesn't exist. */
    @Transactional(readOnly = true)
    public PaymentView getForEnrollment(UUID enrollmentId) {
        return payments.findByEnrollmentId(enrollmentId)
                .map(mapper::toView)
                .orElseThrow(() -> new ResourceNotFoundException("Enrollment", enrollmentId));
    }
}
