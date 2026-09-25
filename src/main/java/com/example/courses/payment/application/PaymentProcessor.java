package com.example.courses.payment.application;

import com.example.courses.enrollment.domain.EnrollmentStatus;
import com.example.courses.messaging.events.EnrollmentCreated;
import com.example.courses.messaging.events.PaymentConfirmed;
import com.example.courses.messaging.events.PaymentFailed;
import com.example.courses.messaging.inbox.IdempotentConsumer;
import com.example.courses.messaging.outbox.OutboxRecorder;
import com.example.courses.payment.domain.Payment;
import com.example.courses.payment.domain.PaymentStatus;
import com.example.courses.payment.repository.PaymentRepository;
import com.example.courses.shared.application.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class PaymentProcessor {

    static final String CONSUMER = "payment-processor";

    private static final Logger log = LoggerFactory.getLogger(PaymentProcessor.class);

    private final PaymentRepository payments;
    private final PaymentGateway gateway;
    private final OutboxRecorder outbox;
    private final IdempotentConsumer idempotentConsumer;

    public PaymentProcessor(PaymentRepository payments, PaymentGateway gateway, OutboxRecorder outbox,
                            IdempotentConsumer idempotentConsumer) {
        this.payments = payments;
        this.gateway = gateway;
        this.outbox = outbox;
        this.idempotentConsumer = idempotentConsumer;
    }

    @Transactional
    public void process(UUID eventId, EnrollmentCreated event) {
        if (!idempotentConsumer.isFirstDelivery(eventId, CONSUMER)) {
            log.debug("Skipping duplicate EnrollmentCreated {}", eventId);
            return;
        }
        Payment payment = payments.findById(event.paymentId())
                .orElseThrow(() -> new ResourceNotFoundException("Payment", event.paymentId()));
        if (payment.getStatus() != PaymentStatus.PENDING) {
            return;
        }
        if (payment.getEnrollment().getStatus() != EnrollmentStatus.PENDING_PAYMENT) {
            decline(payment, "Enrollment is no longer awaiting payment");
            return;
        }
        switch (gateway.charge(payment.getIdempotencyKey(), payment.getAmount(), payment.getCurrency())) {
            case PaymentResult.Approved approved -> {
                payment.confirm();
                outbox.record(new PaymentConfirmed(payment.getId(), event.enrollmentId(), Instant.now()));
                log.info("Payment {} confirmed (transaction {})", payment.getId(), approved.transactionId());
            }
            case PaymentResult.Declined declined -> decline(payment, declined.reason());
        }
    }

    private void decline(Payment payment, String reason) {
        payment.fail();
        outbox.record(new PaymentFailed(payment.getId(), payment.getEnrollment().getId(), reason, Instant.now()));
        log.info("Payment {} failed: {}", payment.getId(), reason);
    }
}
