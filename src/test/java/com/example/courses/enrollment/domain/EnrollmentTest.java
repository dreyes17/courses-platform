package com.example.courses.enrollment.domain;

import com.example.courses.shared.domain.BusinessRuleViolationException;
import com.example.courses.shared.domain.InvalidStateTransitionException;
import org.junit.jupiter.api.Test;

import static com.example.courses.support.DomainFixtures.activeEnrollment;
import static com.example.courses.support.DomainFixtures.completedEnrollment;
import static com.example.courses.support.DomainFixtures.pendingEnrollment;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EnrollmentTest {

    @Test
    void startsPendingPaymentWithNoProgress() {
        Enrollment enrollment = pendingEnrollment();

        assertThat(enrollment.getStatus()).isEqualTo(EnrollmentStatus.PENDING_PAYMENT);
        assertThat(enrollment.getProgress()).isZero();
        assertThat(enrollment.getCompletedAt()).isNull();
    }

    @Test
    void activatesOnlyFromPendingPayment() {
        Enrollment enrollment = activeEnrollment();

        assertThat(enrollment.getStatus()).isEqualTo(EnrollmentStatus.ACTIVE);
        assertThatThrownBy(enrollment::activate).isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    void progressRequiresAnActiveEnrollment() {
        assertThatThrownBy(() -> pendingEnrollment().updateProgress(10))
                .isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    void progressMustStayWithinRangeAndNeverGoBackwards() {
        Enrollment enrollment = activeEnrollment();
        enrollment.updateProgress(40);

        assertThatThrownBy(() -> enrollment.updateProgress(101)).isInstanceOf(BusinessRuleViolationException.class);
        assertThatThrownBy(() -> enrollment.updateProgress(-1)).isInstanceOf(BusinessRuleViolationException.class);
        assertThatThrownBy(() -> enrollment.updateProgress(30)).isInstanceOf(BusinessRuleViolationException.class);
        assertThat(enrollment.getProgress()).isEqualTo(40);
    }

    @Test
    void reachingOneHundredCompletesTheEnrollment() {
        Enrollment enrollment = activeEnrollment();

        assertThat(enrollment.updateProgress(99)).isFalse();
        assertThat(enrollment.updateProgress(100)).isTrue();
        assertThat(enrollment.getStatus()).isEqualTo(EnrollmentStatus.COMPLETED);
        assertThat(enrollment.getCompletedAt()).isNotNull();
    }

    @Test
    void canBeCancelledWhilePendingOrActiveButNotOnceCompleted() {
        Enrollment pending = pendingEnrollment();
        pending.cancel();
        Enrollment active = activeEnrollment();
        active.cancel();

        assertThat(pending.getStatus()).isEqualTo(EnrollmentStatus.CANCELLED);
        assertThat(active.getStatus()).isEqualTo(EnrollmentStatus.CANCELLED);
        assertThatThrownBy(pending::cancel).isInstanceOf(InvalidStateTransitionException.class);
        assertThatThrownBy(() -> completedEnrollment().cancel()).isInstanceOf(InvalidStateTransitionException.class);
    }
}
