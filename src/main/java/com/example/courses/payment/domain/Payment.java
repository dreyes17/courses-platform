package com.example.courses.payment.domain;

import com.example.courses.enrollment.domain.Enrollment;
import com.example.courses.shared.domain.BaseEntity;
import com.example.courses.shared.domain.BusinessRuleViolationException;
import com.example.courses.shared.domain.InvalidStateTransitionException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "payments")
public class Payment extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "enrollment_id", nullable = false)
    private Enrollment enrollment;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status;

    @Column(name = "idempotency_key", nullable = false, unique = true)
    private String idempotencyKey;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected Payment() {
    }

    private Payment(Enrollment enrollment, BigDecimal amount, String currency, String idempotencyKey) {
        if (amount.signum() < 0) {
            throw new BusinessRuleViolationException("amount cannot be negative");
        }
        this.enrollment = Objects.requireNonNull(enrollment, "enrollment");
        this.amount = amount;
        this.currency = Objects.requireNonNull(currency, "currency");
        this.status = PaymentStatus.PENDING;
        this.idempotencyKey = Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        this.createdAt = Instant.now();
    }

    public static Payment requestFor(Enrollment enrollment, BigDecimal amount, String currency,
                                      String idempotencyKey) {
        return new Payment(enrollment, amount, currency, idempotencyKey);
    }

    public void confirm() {
        if (status != PaymentStatus.PENDING) {
            throw new InvalidStateTransitionException("Payment", getId(), status, "be confirmed");
        }
        status = PaymentStatus.CONFIRMED;
    }

    public void fail() {
        if (status != PaymentStatus.PENDING) {
            throw new InvalidStateTransitionException("Payment", getId(), status, "be marked as failed");
        }
        status = PaymentStatus.FAILED;
    }

    public Enrollment getEnrollment() {
        return enrollment;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
