package com.example.courses.enrollment;

import com.example.courses.AbstractIntegrationTest;
import com.example.courses.catalog.domain.CourseFullException;
import com.example.courses.certificate.repository.CertificateRepository;
import com.example.courses.enrollment.application.AlreadyEnrolledException;
import com.example.courses.enrollment.application.EnrollmentService;
import com.example.courses.enrollment.application.EnrollmentView;
import com.example.courses.enrollment.domain.EnrollmentStatus;
import com.example.courses.idempotency.application.IdempotencyKeyReusedException;
import com.example.courses.payment.domain.PaymentStatus;
import com.example.courses.payment.repository.PaymentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

class EnrollmentFlowTest extends AbstractIntegrationTest {

    private static final Duration ASYNC_TIMEOUT = Duration.ofSeconds(15);

    @Autowired
    private EnrollmentService enrollmentService;
    @Autowired
    private PaymentRepository payments;
    @Autowired
    private CertificateRepository certificates;

    @Test
    void confirmedPaymentActivatesEnrollmentAndCompletionIssuesCertificate() {
        UUID courseId = publishedCourse(10, new BigDecimal("99.00"));
        UUID studentId = student();

        EnrollmentView enrollment = enrollmentService.enroll(studentId, courseId, null);

        assertThat(enrollment.status()).isEqualTo(EnrollmentStatus.PENDING_PAYMENT);
        assertThat(seatsTaken(courseId)).isEqualTo(1);
        await().atMost(ASYNC_TIMEOUT).untilAsserted(() ->
                assertThat(enrollmentService.get(enrollment.id()).status()).isEqualTo(EnrollmentStatus.ACTIVE));
        assertThat(payments.findByEnrollmentId(enrollment.id()).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.CONFIRMED);

        EnrollmentView completed = enrollmentService.updateProgress(enrollment.id(), 100);

        assertThat(completed.status()).isEqualTo(EnrollmentStatus.COMPLETED);
        await().atMost(ASYNC_TIMEOUT).untilAsserted(() ->
                assertThat(certificates.findByEnrollmentId(enrollment.id())).isPresent());
    }

    @Test
    void declinedPaymentCancelsEnrollmentAndReleasesTheSeat() {
        UUID courseId = publishedCourse(10, new BigDecimal("20000.00"));

        EnrollmentView enrollment = enrollmentService.enroll(student(), courseId, null);

        await().atMost(ASYNC_TIMEOUT).untilAsserted(() ->
                assertThat(enrollmentService.get(enrollment.id()).status()).isEqualTo(EnrollmentStatus.CANCELLED));
        assertThat(payments.findByEnrollmentId(enrollment.id()).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.FAILED);
        assertThat(seatsTaken(courseId)).isZero();
    }

    @Test
    void cancellingReleasesTheSeatSoAnotherStudentCanTakeIt() {
        UUID courseId = publishedCourse(1, new BigDecimal("10.00"));
        EnrollmentView first = enrollmentService.enroll(student(), courseId, null);
        UUID secondStudent = student();
        assertThatThrownBy(() -> enrollmentService.enroll(secondStudent, courseId, null))
                .isInstanceOf(CourseFullException.class);

        enrollmentService.cancel(first.id());

        assertThat(seatsTaken(courseId)).isZero();
        assertThat(enrollmentService.enroll(secondStudent, courseId, null).status())
                .isEqualTo(EnrollmentStatus.PENDING_PAYMENT);
    }

    @Test
    void retryWithTheSameIdempotencyKeyReturnsTheOriginalEnrollment() {
        UUID courseId = publishedCourse(10, new BigDecimal("10.00"));
        UUID studentId = student();
        String key = UUID.randomUUID().toString();

        EnrollmentView first = enrollmentService.enroll(studentId, courseId, key);
        EnrollmentView retry = enrollmentService.enroll(studentId, courseId, key);

        assertThat(retry.id()).isEqualTo(first.id());
        assertThat(seatsTaken(courseId)).isEqualTo(1);
    }

    @Test
    void reusingAnIdempotencyKeyForADifferentRequestIsRejected() {
        UUID studentId = student();
        String key = UUID.randomUUID().toString();
        enrollmentService.enroll(studentId, publishedCourse(10, BigDecimal.TEN), key);
        UUID otherCourse = publishedCourse(10, BigDecimal.TEN);

        assertThatThrownBy(() -> enrollmentService.enroll(studentId, otherCourse, key))
                .isInstanceOf(IdempotencyKeyReusedException.class);
        assertThat(seatsTaken(otherCourse)).isZero();
    }

    @Test
    void studentCannotHoldTwoActiveEnrollmentsInTheSameCourse() {
        UUID courseId = publishedCourse(10, BigDecimal.TEN);
        UUID studentId = student();
        enrollmentService.enroll(studentId, courseId, null);

        assertThatThrownBy(() -> enrollmentService.enroll(studentId, courseId, null))
                .isInstanceOf(AlreadyEnrolledException.class);
        assertThat(seatsTaken(courseId)).isEqualTo(1);
    }
}
