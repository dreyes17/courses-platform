package com.example.courses.payment.domain;

import java.util.UUID;

public class InvalidPaymentStateException extends RuntimeException {

    public InvalidPaymentStateException(UUID paymentId, PaymentStatus current, String attemptedAction) {
        super("Payment %s cannot %s while in status %s".formatted(paymentId, attemptedAction, current));
    }
}
