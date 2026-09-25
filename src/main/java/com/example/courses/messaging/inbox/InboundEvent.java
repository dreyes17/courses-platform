package com.example.courses.messaging.inbox;

import com.example.courses.messaging.events.DomainEvent;

import java.util.UUID;

public record InboundEvent<T extends DomainEvent>(UUID eventId, T payload) {
}
