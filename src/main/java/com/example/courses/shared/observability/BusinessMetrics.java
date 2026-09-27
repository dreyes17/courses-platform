package com.example.courses.shared.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

/**
 * Business counters exported through Micrometer; in Prometheus they read e.g.
 * {@code courses_enrollments_total{outcome="created"}}. Every outcome is registered up front so dashboards
 * see a 0 rather than a missing series. Successes are counted only after the transaction commits, so a
 * rolled-back operation is never counted.
 */
@Component
public class BusinessMetrics {

    public enum EnrollmentRejection { COURSE_FULL, ALREADY_ENROLLED }

    private final Counter enrollmentsCreated;
    private final Map<EnrollmentRejection, Counter> enrollmentsRejected = new EnumMap<>(EnrollmentRejection.class);
    private final Counter paymentsConfirmed;
    private final Counter paymentsFailed;
    private final Counter certificatesIssued;

    public BusinessMetrics(MeterRegistry registry) {
        this.enrollmentsCreated = enrollmentCounter(registry, "created");
        for (EnrollmentRejection rejection : EnrollmentRejection.values()) {
            enrollmentsRejected.put(rejection,
                    enrollmentCounter(registry, rejection.name().toLowerCase(Locale.ROOT)));
        }
        this.paymentsConfirmed = paymentCounter(registry, "confirmed");
        this.paymentsFailed = paymentCounter(registry, "failed");
        this.certificatesIssued = Counter.builder("courses.certificates.issued")
                .description("Certificates issued for completed enrollments").register(registry);
    }

    public void enrollmentCreated() {
        afterCommit(enrollmentsCreated::increment);
    }

    /** Counted immediately: the rejection is the outcome, even though its transaction rolls back. */
    public void enrollmentRejected(EnrollmentRejection rejection) {
        enrollmentsRejected.get(rejection).increment();
    }

    public void paymentConfirmed() {
        afterCommit(paymentsConfirmed::increment);
    }

    public void paymentFailed() {
        afterCommit(paymentsFailed::increment);
    }

    public void certificateIssued() {
        afterCommit(certificatesIssued::increment);
    }

    private static Counter enrollmentCounter(MeterRegistry registry, String outcome) {
        return Counter.builder("courses.enrollments")
                .description("Enrollment attempts by outcome: created, or the reason it was rejected")
                .tag("outcome", outcome)
                .register(registry);
    }

    private static Counter paymentCounter(MeterRegistry registry, String outcome) {
        return Counter.builder("courses.payments.processed")
                .description("Payments processed by the payment consumer, by outcome")
                .tag("outcome", outcome)
                .register(registry);
    }

    private static void afterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }
}
