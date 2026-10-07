package io.github.dreyes17.courses.payment.application;

import io.github.dreyes17.courses.enrollment.domain.EnrollmentStatus;
import io.github.dreyes17.courses.messaging.events.EnrollmentCreated;
import io.github.dreyes17.courses.messaging.events.PaymentConfirmed;
import io.github.dreyes17.courses.messaging.events.PaymentFailed;
import io.github.dreyes17.courses.messaging.inbox.IdempotentConsumer;
import io.github.dreyes17.courses.messaging.outbox.OutboxRecorder;
import io.github.dreyes17.courses.payment.domain.Payment;
import io.github.dreyes17.courses.payment.domain.PaymentStatus;
import io.github.dreyes17.courses.payment.repository.PaymentRepository;
import io.github.dreyes17.courses.shared.application.ResourceNotFoundException;
import io.github.dreyes17.courses.shared.observability.BusinessMetrics;
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
    private final BusinessMetrics metrics;

    public PaymentProcessor(PaymentRepository payments, PaymentGateway gateway, OutboxRecorder outbox,
                            IdempotentConsumer idempotentConsumer, BusinessMetrics metrics) {
        this.payments = payments;
        this.gateway = gateway;
        this.outbox = outbox;
        this.idempotentConsumer = idempotentConsumer;
        this.metrics = metrics;
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
                metrics.paymentConfirmed();
                log.info("Payment {} confirmed (transaction {})", payment.getId(), approved.transactionId());
            }
            case PaymentResult.Declined declined -> decline(payment, declined.reason());
        }
    }

    private void decline(Payment payment, String reason) {
        payment.fail(reason);
        outbox.record(new PaymentFailed(payment.getId(), payment.getEnrollment().getId(), reason, Instant.now()));
        metrics.paymentFailed();
        log.info("Payment {} failed: {}", payment.getId(), reason);
    }
}
