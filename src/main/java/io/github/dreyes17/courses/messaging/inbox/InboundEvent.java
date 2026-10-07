package io.github.dreyes17.courses.messaging.inbox;

import io.github.dreyes17.courses.messaging.events.DomainEvent;

import java.util.UUID;

public record InboundEvent<T extends DomainEvent>(UUID eventId, T payload) {
}
