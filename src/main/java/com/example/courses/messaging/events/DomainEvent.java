package com.example.courses.messaging.events;

import java.util.UUID;

/**
 * Event payloads published through the outbox. They are the versioned wire contract, deliberately
 * separate from the JPA entities.
 */
public sealed interface DomainEvent
        permits EnrollmentCreated, PaymentConfirmed, PaymentFailed, EnrollmentCompleted {

    EventType type();

    UUID aggregateId();
}
