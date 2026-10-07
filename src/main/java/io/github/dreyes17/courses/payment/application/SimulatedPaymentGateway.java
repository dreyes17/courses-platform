package io.github.dreyes17.courses.payment.application;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

/** Stands in for a real provider: approves any charge up to a configurable limit. */
@Component
public class SimulatedPaymentGateway implements PaymentGateway {

    private final BigDecimal declineAbove;

    public SimulatedPaymentGateway(@Value("${app.payments.simulation.decline-above:10000}") BigDecimal declineAbove) {
        this.declineAbove = declineAbove;
    }

    @Override
    public PaymentResult charge(String idempotencyKey, BigDecimal amount, String currency) {
        if (amount.compareTo(declineAbove) > 0) {
            return new PaymentResult.Declined("Amount %s %s exceeds the simulated limit".formatted(amount, currency));
        }
        return new PaymentResult.Approved(UUID.nameUUIDFromBytes(idempotencyKey.getBytes()).toString());
    }
}
