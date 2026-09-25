package com.example.courses.enrollment.application;

import com.example.courses.catalog.domain.Course;
import com.example.courses.catalog.domain.CourseFullException;
import com.example.courses.catalog.repository.CourseRepository;
import com.example.courses.enrollment.domain.Enrollment;
import com.example.courses.enrollment.domain.EnrollmentStatus;
import com.example.courses.enrollment.domain.Student;
import com.example.courses.enrollment.repository.EnrollmentRepository;
import com.example.courses.enrollment.repository.StudentRepository;
import com.example.courses.idempotency.application.IdempotentRequests;
import com.example.courses.messaging.events.DomainEvent;
import com.example.courses.messaging.events.EnrollmentCompleted;
import com.example.courses.messaging.events.EnrollmentCreated;
import com.example.courses.messaging.outbox.OutboxRecorder;
import com.example.courses.payment.domain.Payment;
import com.example.courses.payment.domain.PaymentStatus;
import com.example.courses.payment.repository.PaymentRepository;
import com.example.courses.shared.application.ResourceNotFoundException;
import com.example.courses.shared.domain.InvalidStateTransitionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static com.example.courses.support.DomainFixtures.activeEnrollment;
import static com.example.courses.support.DomainFixtures.draftCourse;
import static com.example.courses.support.DomainFixtures.publishedCourse;
import static com.example.courses.support.DomainFixtures.student;
import static com.example.courses.support.DomainFixtures.withId;
import static com.example.courses.support.DomainFixtures.withSeatsTaken;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EnrollmentServiceTest {

    @Mock
    private StudentRepository students;
    @Mock
    private CourseRepository courses;
    @Mock
    private EnrollmentRepository enrollments;
    @Mock
    private PaymentRepository payments;
    @Mock
    private OutboxRecorder outbox;
    @Mock
    private IdempotentRequests idempotentRequests;

    private EnrollmentService service;
    private final Student student = student();
    private final Course course = publishedCourse(10, new BigDecimal("49.90"));

    @BeforeEach
    void setUp() {
        service = new EnrollmentService(students, courses, enrollments, payments, outbox, idempotentRequests,
                JsonMapper.builder().build(), "EUR");
    }

    @Test
    void enrollReservesSeatCreatesPendingPaymentAndRecordsEnrollmentCreated() {
        givenNotEnrolled();
        when(courses.tryReserveSeat(course.getId())).thenReturn(1);
        when(students.findById(student.getId())).thenReturn(Optional.of(student));
        when(courses.findById(course.getId())).thenReturn(Optional.of(course));
        when(enrollments.saveAndFlush(any())).thenAnswer(invocation -> withId(invocation.getArgument(0)));
        when(payments.save(any())).thenAnswer(invocation -> withId(invocation.getArgument(0)));

        EnrollmentView view = service.enroll(student.getId(), course.getId(), null);

        assertThat(view.status()).isEqualTo(EnrollmentStatus.PENDING_PAYMENT);
        assertThat(view.studentId()).isEqualTo(student.getId());
        var payment = ArgumentCaptor.forClass(Payment.class);
        verify(payments).save(payment.capture());
        assertThat(payment.getValue().getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(payment.getValue().getAmount()).isEqualByComparingTo("49.90");
        assertThat(payment.getValue().getIdempotencyKey()).isEqualTo("enrollment-" + view.id());
        var event = ArgumentCaptor.forClass(DomainEvent.class);
        verify(outbox).record(event.capture());
        assertThat(event.getValue()).isInstanceOfSatisfying(EnrollmentCreated.class, created -> {
            assertThat(created.enrollmentId()).isEqualTo(view.id());
            assertThat(created.paymentId()).isEqualTo(payment.getValue().getId());
            assertThat(created.currency()).isEqualTo("EUR");
        });
    }

    @Test
    void alreadyEnrolledStudentIsRejectedBeforeTouchingTheSeatCount() {
        when(enrollments.existsByCourseIdAndStudentIdAndStatusIn(eq(course.getId()), eq(student.getId()), any()))
                .thenReturn(true);

        assertThatThrownBy(() -> service.enroll(student.getId(), course.getId(), null))
                .isInstanceOf(AlreadyEnrolledException.class);
        verify(courses, never()).tryReserveSeat(any());
        verifyNoInteractions(outbox);
    }

    @Test
    void whenNoSeatIsReservedTheCourseExplainsWhy() {
        givenNotEnrolled();
        Course full = withSeatsTaken(publishedCourse(3, BigDecimal.TEN), 3);
        when(courses.tryReserveSeat(any())).thenReturn(0);
        when(courses.findById(full.getId())).thenReturn(Optional.of(full));

        assertThatThrownBy(() -> service.enroll(student.getId(), full.getId(), null))
                .isInstanceOf(CourseFullException.class);
        verify(enrollments, never()).saveAndFlush(any());
        verifyNoInteractions(payments, outbox);
    }

    @Test
    void enrollingInAnUnpublishedCourseIsAnInvalidTransition() {
        givenNotEnrolled();
        Course draft = draftCourse(3, BigDecimal.TEN);
        when(courses.tryReserveSeat(any())).thenReturn(0);
        when(courses.findById(draft.getId())).thenReturn(Optional.of(draft));

        assertThatThrownBy(() -> service.enroll(student.getId(), draft.getId(), null))
                .isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    void enrollingInAnUnknownCourseIsNotFound() {
        givenNotEnrolled();
        when(courses.tryReserveSeat(any())).thenReturn(0);
        when(courses.findById(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.enroll(student.getId(), UUID.randomUUID(), null))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void concurrentDuplicateCaughtByTheUniqueIndexBecomesAlreadyEnrolled() {
        givenNotEnrolled();
        when(courses.tryReserveSeat(course.getId())).thenReturn(1);
        when(students.findById(student.getId())).thenReturn(Optional.of(student));
        when(courses.findById(course.getId())).thenReturn(Optional.of(course));
        when(enrollments.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("uq_enrollments_active"));

        assertThatThrownBy(() -> service.enroll(student.getId(), course.getId(), null))
                .isInstanceOf(AlreadyEnrolledException.class);
        verifyNoInteractions(payments, outbox);
    }

    @Test
    void withAnIdempotencyKeyTheEnrollmentRunsThroughIdempotentRequests() {
        UUID enrollmentId = UUID.randomUUID();
        var replayed = new EnrollmentView(enrollmentId, student.getId(), course.getId(),
                EnrollmentStatus.PENDING_PAYMENT, 0, null, null);
        when(idempotentRequests.<EnrollmentView>execute(eq("key-1"), eq(EnrollmentService.ENROLL_ENDPOINT),
                eq(student.getId() + ":" + course.getId()), anyInt(), any(), any(), any()))
                .thenReturn(replayed);

        assertThat(service.enroll(student.getId(), course.getId(), "key-1")).isSameAs(replayed);
        verifyNoInteractions(courses, enrollments, payments, outbox);
    }

    @Test
    void completingTheCourseRecordsEnrollmentCompleted() {
        Enrollment enrollment = activeEnrollment();
        when(enrollments.findById(enrollment.getId())).thenReturn(Optional.of(enrollment));

        service.updateProgress(enrollment.getId(), 100);

        var event = ArgumentCaptor.forClass(DomainEvent.class);
        verify(outbox).record(event.capture());
        assertThat(event.getValue()).isInstanceOfSatisfying(EnrollmentCompleted.class,
                completed -> assertThat(completed.enrollmentId()).isEqualTo(enrollment.getId()));
    }

    @Test
    void partialProgressRecordsNoEvent() {
        Enrollment enrollment = activeEnrollment();
        when(enrollments.findById(enrollment.getId())).thenReturn(Optional.of(enrollment));

        assertThat(service.updateProgress(enrollment.getId(), 60).progress()).isEqualTo(60);
        verifyNoInteractions(outbox);
    }

    @Test
    void cancellingReleasesTheSeatOfThatCourse() {
        Enrollment enrollment = activeEnrollment();
        when(enrollments.findById(enrollment.getId())).thenReturn(Optional.of(enrollment));

        EnrollmentView view = service.cancel(enrollment.getId());

        assertThat(view.status()).isEqualTo(EnrollmentStatus.CANCELLED);
        verify(courses).releaseSeat(enrollment.getCourse().getId());
    }

    private void givenNotEnrolled() {
        when(enrollments.existsByCourseIdAndStudentIdAndStatusIn(any(), any(), any())).thenReturn(false);
    }
}
