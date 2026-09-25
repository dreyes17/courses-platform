package com.example.courses.payment.application;

public sealed interface PaymentResult {

    record Approved(String transactionId) implements PaymentResult {
    }

    record Declined(String reason) implements PaymentResult {
    }
}
