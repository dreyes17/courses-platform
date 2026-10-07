package io.github.dreyes17.courses.payment.application;

import java.math.BigDecimal;

public interface PaymentGateway {

    /** The idempotency key lets the provider ignore a repeated charge when a delivery is retried. */
    PaymentResult charge(String idempotencyKey, BigDecimal amount, String currency);
}
