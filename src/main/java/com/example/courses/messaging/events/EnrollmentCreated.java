package com.example.courses.messaging.events;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record EnrollmentCreated(
        UUID enrollmentId,
        UUID studentId,
        UUID courseId,
        UUID paymentId,
        BigDecimal amount,
        String currency,
        Instant occurredAt
) implements DomainEvent {

    @Override
    public EventType type() {
        return EventType.ENROLLMENT_CREATED;
    }

    @Override
    public UUID aggregateId() {
        return enrollmentId;
    }
}
