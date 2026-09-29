package com.example.courses.payment.application;

import com.example.courses.payment.domain.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** The payment of an enrollment. failureReason is set only when the status is FAILED. */
public record PaymentView(
        UUID id,
        UUID enrollmentId,
        BigDecimal amount,
        String currency,
        PaymentStatus status,
        String failureReason,
        Instant createdAt
) {
}
