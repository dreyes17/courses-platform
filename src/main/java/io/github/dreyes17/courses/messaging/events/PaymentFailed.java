package io.github.dreyes17.courses.messaging.events;

import java.time.Instant;
import java.util.UUID;

public record PaymentFailed(UUID paymentId, UUID enrollmentId, String reason, Instant occurredAt)
        implements DomainEvent {

    @Override
    public EventType type() {
        return EventType.PAYMENT_FAILED;
    }

    @Override
    public UUID aggregateId() {
        return paymentId;
    }
}
