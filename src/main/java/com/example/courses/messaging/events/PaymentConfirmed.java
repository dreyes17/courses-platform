package com.example.courses.messaging.events;

import java.time.Instant;
import java.util.UUID;

public record PaymentConfirmed(UUID paymentId, UUID enrollmentId, Instant occurredAt) implements DomainEvent {

    @Override
    public EventType type() {
        return EventType.PAYMENT_CONFIRMED;
    }

    @Override
    public UUID aggregateId() {
        return paymentId;
    }
}
