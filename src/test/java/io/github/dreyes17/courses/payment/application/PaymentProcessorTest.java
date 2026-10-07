package io.github.dreyes17.courses.payment.application;

import io.github.dreyes17.courses.enrollment.domain.Enrollment;
import io.github.dreyes17.courses.messaging.events.DomainEvent;
import io.github.dreyes17.courses.messaging.events.EnrollmentCreated;
import io.github.dreyes17.courses.messaging.events.PaymentConfirmed;
import io.github.dreyes17.courses.messaging.events.PaymentFailed;
import io.github.dreyes17.courses.messaging.inbox.IdempotentConsumer;
import io.github.dreyes17.courses.messaging.outbox.OutboxRecorder;
import io.github.dreyes17.courses.payment.domain.Payment;
import io.github.dreyes17.courses.payment.domain.PaymentStatus;
import io.github.dreyes17.courses.payment.repository.PaymentRepository;
import io.github.dreyes17.courses.shared.observability.BusinessMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static io.github.dreyes17.courses.support.DomainFixtures.pendingEnrollment;
import static io.github.dreyes17.courses.support.DomainFixtures.pendingPayment;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentProcessorTest {

    private static final UUID EVENT_ID = UUID.randomUUID();

    @Mock
    private PaymentRepository payments;
    @Mock
    private PaymentGateway gateway;
    @Mock
    private OutboxRecorder outbox;
    @Mock
    private IdempotentConsumer idempotentConsumer;
    @Mock
    private BusinessMetrics metrics;
    @InjectMocks
    private PaymentProcessor processor;

    private Enrollment enrollment;
    private Payment payment;

    @BeforeEach
    void setUp() {
        enrollment = pendingEnrollment();
        payment = pendingPayment(enrollment);
    }

    @Test
    void approvedChargeConfirmsThePaymentAndRecordsPaymentConfirmed() {
        givenFirstDelivery();
        when(payments.findById(payment.getId())).thenReturn(Optional.of(payment));
        when(gateway.charge(payment.getIdempotencyKey(), payment.getAmount(), "EUR"))
                .thenReturn(new PaymentResult.Approved("tx-1"));

        processor.process(EVENT_ID, event());

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CONFIRMED);
        verify(metrics).paymentConfirmed();
        assertThat(recordedEvent()).isInstanceOfSatisfying(PaymentConfirmed.class,
                confirmed -> assertThat(confirmed.enrollmentId()).isEqualTo(enrollment.getId()));
    }

    @Test
    void declinedChargeFailsThePaymentAndRecordsPaymentFailedWithTheReason() {
        givenFirstDelivery();
        when(payments.findById(payment.getId())).thenReturn(Optional.of(payment));
        when(gateway.charge(any(), any(), any())).thenReturn(new PaymentResult.Declined("insufficient funds"));

        processor.process(EVENT_ID, event());

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(payment.getFailureReason()).isEqualTo("insufficient funds");
        verify(metrics).paymentFailed();
        assertThat(recordedEvent()).isInstanceOfSatisfying(PaymentFailed.class,
                failed -> assertThat(failed.reason()).isEqualTo("insufficient funds"));
    }

    @Test
    void enrollmentCancelledBeforeChargingIsNeverCharged() {
        givenFirstDelivery();
        enrollment.cancel();
        when(payments.findById(payment.getId())).thenReturn(Optional.of(payment));

        processor.process(EVENT_ID, event());

        verifyNoInteractions(gateway);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(payment.getFailureReason()).isEqualTo("Enrollment is no longer awaiting payment");
        assertThat(recordedEvent()).isInstanceOf(PaymentFailed.class);
    }

    @Test
    void duplicateDeliveryIsSkippedWithoutChargingAgain() {
        when(idempotentConsumer.isFirstDelivery(EVENT_ID, PaymentProcessor.CONSUMER)).thenReturn(false);

        processor.process(EVENT_ID, event());

        verifyNoInteractions(payments, gateway, outbox, metrics);
    }

    @Test
    void paymentAlreadySettledIsLeftUntouched() {
        givenFirstDelivery();
        payment.confirm();
        when(payments.findById(payment.getId())).thenReturn(Optional.of(payment));

        processor.process(EVENT_ID, event());

        verifyNoInteractions(gateway, outbox);
    }

    private void givenFirstDelivery() {
        when(idempotentConsumer.isFirstDelivery(EVENT_ID, PaymentProcessor.CONSUMER)).thenReturn(true);
    }

    private EnrollmentCreated event() {
        return new EnrollmentCreated(enrollment.getId(), enrollment.getStudent().getId(),
                enrollment.getCourse().getId(), payment.getId(), payment.getAmount(), "EUR", Instant.now());
    }

    private DomainEvent recordedEvent() {
        var event = ArgumentCaptor.forClass(DomainEvent.class);
        verify(outbox).record(event.capture());
        return event.getValue();
    }
}
