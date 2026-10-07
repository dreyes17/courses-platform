package io.github.dreyes17.courses.enrollment.application;

import io.github.dreyes17.courses.catalog.repository.CourseRepository;
import io.github.dreyes17.courses.enrollment.domain.Enrollment;
import io.github.dreyes17.courses.enrollment.domain.EnrollmentStatus;
import io.github.dreyes17.courses.enrollment.repository.EnrollmentRepository;
import io.github.dreyes17.courses.messaging.events.PaymentConfirmed;
import io.github.dreyes17.courses.messaging.events.PaymentFailed;
import io.github.dreyes17.courses.messaging.inbox.IdempotentConsumer;
import io.github.dreyes17.courses.shared.application.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static io.github.dreyes17.courses.support.DomainFixtures.activeEnrollment;
import static io.github.dreyes17.courses.support.DomainFixtures.pendingEnrollment;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentOutcomeHandlerTest {

    private static final UUID EVENT_ID = UUID.randomUUID();

    @Mock
    private EnrollmentRepository enrollments;
    @Mock
    private CourseRepository courses;
    @Mock
    private IdempotentConsumer idempotentConsumer;
    @InjectMocks
    private PaymentOutcomeHandler handler;

    @Test
    void confirmedPaymentActivatesAPendingEnrollment() {
        Enrollment enrollment = givenFirstDeliveryOf(pendingEnrollment(), PaymentOutcomeHandler.ACTIVATION_CONSUMER);

        handler.onPaymentConfirmed(EVENT_ID, confirmed(enrollment));

        assertThat(enrollment.getStatus()).isEqualTo(EnrollmentStatus.ACTIVE);
    }

    @Test
    void confirmationForACancelledEnrollmentDoesNotReviveIt() {
        Enrollment enrollment = pendingEnrollment();
        enrollment.cancel();
        givenFirstDeliveryOf(enrollment, PaymentOutcomeHandler.ACTIVATION_CONSUMER);

        handler.onPaymentConfirmed(EVENT_ID, confirmed(enrollment));

        assertThat(enrollment.getStatus()).isEqualTo(EnrollmentStatus.CANCELLED);
    }

    @Test
    void duplicateConfirmationIsSkipped() {
        when(idempotentConsumer.isFirstDelivery(EVENT_ID, PaymentOutcomeHandler.ACTIVATION_CONSUMER)).thenReturn(false);

        handler.onPaymentConfirmed(EVENT_ID, new PaymentConfirmed(UUID.randomUUID(), UUID.randomUUID(), Instant.now()));

        verifyNoInteractions(enrollments);
    }

    @Test
    void unknownEnrollmentFailsSoTheMessageIsRetriedThenDeadLettered() {
        when(idempotentConsumer.isFirstDelivery(any(), any())).thenReturn(true);
        when(enrollments.findById(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> handler.onPaymentConfirmed(EVENT_ID,
                new PaymentConfirmed(UUID.randomUUID(), UUID.randomUUID(), Instant.now())))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void failedPaymentCancelsThePendingEnrollmentAndReleasesItsSeat() {
        Enrollment enrollment = givenFirstDeliveryOf(pendingEnrollment(), PaymentOutcomeHandler.PAYMENT_FAILURE_CONSUMER);

        handler.onPaymentFailed(EVENT_ID, failed(enrollment));

        assertThat(enrollment.getStatus()).isEqualTo(EnrollmentStatus.CANCELLED);
        verify(courses).releaseSeat(enrollment.getCourse().getId());
    }

    @Test
    void failedPaymentForAnEnrollmentNoLongerPendingReleasesNothing() {
        Enrollment enrollment = givenFirstDeliveryOf(activeEnrollment(), PaymentOutcomeHandler.PAYMENT_FAILURE_CONSUMER);

        handler.onPaymentFailed(EVENT_ID, failed(enrollment));

        assertThat(enrollment.getStatus()).isEqualTo(EnrollmentStatus.ACTIVE);
        verifyNoInteractions(courses);
    }

    private Enrollment givenFirstDeliveryOf(Enrollment enrollment, String consumer) {
        when(idempotentConsumer.isFirstDelivery(EVENT_ID, consumer)).thenReturn(true);
        when(enrollments.findById(eq(enrollment.getId()))).thenReturn(Optional.of(enrollment));
        return enrollment;
    }

    private static PaymentConfirmed confirmed(Enrollment enrollment) {
        return new PaymentConfirmed(UUID.randomUUID(), enrollment.getId(), Instant.now());
    }

    private static PaymentFailed failed(Enrollment enrollment) {
        return new PaymentFailed(UUID.randomUUID(), enrollment.getId(), "declined", Instant.now());
    }
}
