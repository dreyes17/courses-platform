package com.example.courses.payment.application;

import com.example.courses.enrollment.domain.Enrollment;
import com.example.courses.messaging.events.DomainEvent;
import com.example.courses.messaging.events.EnrollmentCreated;
import com.example.courses.messaging.events.PaymentConfirmed;
import com.example.courses.messaging.events.PaymentFailed;
import com.example.courses.messaging.inbox.IdempotentConsumer;
import com.example.courses.messaging.outbox.OutboxRecorder;
import com.example.courses.payment.domain.Payment;
import com.example.courses.payment.domain.PaymentStatus;
import com.example.courses.payment.repository.PaymentRepository;
import com.example.courses.shared.observability.BusinessMetrics;
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

import static com.example.courses.support.DomainFixtures.pendingEnrollment;
import static com.example.courses.support.DomainFixtures.pendingPayment;
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
