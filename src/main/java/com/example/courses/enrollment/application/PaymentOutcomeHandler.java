package com.example.courses.enrollment.application;

import com.example.courses.catalog.repository.CourseRepository;
import com.example.courses.enrollment.domain.Enrollment;
import com.example.courses.enrollment.domain.EnrollmentStatus;
import com.example.courses.enrollment.repository.EnrollmentRepository;
import com.example.courses.messaging.events.PaymentConfirmed;
import com.example.courses.messaging.events.PaymentFailed;
import com.example.courses.messaging.inbox.IdempotentConsumer;
import com.example.courses.shared.application.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class PaymentOutcomeHandler {

    static final String ACTIVATION_CONSUMER = "enrollment-activation";
    static final String PAYMENT_FAILURE_CONSUMER = "enrollment-payment-failure";

    private static final Logger log = LoggerFactory.getLogger(PaymentOutcomeHandler.class);

    private final EnrollmentRepository enrollments;
    private final CourseRepository courses;
    private final IdempotentConsumer idempotentConsumer;

    public PaymentOutcomeHandler(EnrollmentRepository enrollments, CourseRepository courses,
                                 IdempotentConsumer idempotentConsumer) {
        this.enrollments = enrollments;
        this.courses = courses;
        this.idempotentConsumer = idempotentConsumer;
    }

    @Transactional
    public void onPaymentConfirmed(UUID eventId, PaymentConfirmed event) {
        if (!idempotentConsumer.isFirstDelivery(eventId, ACTIVATION_CONSUMER)) {
            log.debug("Skipping duplicate PaymentConfirmed {}", eventId);
            return;
        }
        Enrollment enrollment = findEnrollment(event.enrollmentId());
        if (enrollment.getStatus() != EnrollmentStatus.PENDING_PAYMENT) {
            log.warn("Payment {} confirmed for enrollment {} in status {}; it needs a manual refund",
                    event.paymentId(), enrollment.getId(), enrollment.getStatus());
            return;
        }
        enrollment.activate();
    }

    @Transactional
    public void onPaymentFailed(UUID eventId, PaymentFailed event) {
        if (!idempotentConsumer.isFirstDelivery(eventId, PAYMENT_FAILURE_CONSUMER)) {
            log.debug("Skipping duplicate PaymentFailed {}", eventId);
            return;
        }
        Enrollment enrollment = findEnrollment(event.enrollmentId());
        if (enrollment.getStatus() != EnrollmentStatus.PENDING_PAYMENT) {
            return;
        }
        enrollment.cancel();
        UUID courseId = enrollment.getCourse().getId();
        courses.releaseSeat(courseId);
        log.info("Enrollment {} cancelled after failed payment {}: {}",
                enrollment.getId(), event.paymentId(), event.reason());
    }

    private Enrollment findEnrollment(UUID enrollmentId) {
        return enrollments.findById(enrollmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Enrollment", enrollmentId));
    }
}
