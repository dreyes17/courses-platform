package com.example.courses.messaging.outbox;

import com.example.courses.messaging.events.DomainEvent;
import com.example.courses.shared.observability.CorrelationId;
import com.example.courses.shared.observability.TracePropagation;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * MANDATORY propagation: an event is only ever written as part of the business transaction that
 * produced it, never on its own.
 */
@Component
public class OutboxRecorder {

    private final OutboxEventRepository outboxEvents;
    private final JsonMapper jsonMapper;
    private final TracePropagation tracePropagation;

    public OutboxRecorder(OutboxEventRepository outboxEvents, JsonMapper jsonMapper,
                          TracePropagation tracePropagation) {
        this.outboxEvents = outboxEvents;
        this.jsonMapper = jsonMapper;
        this.tracePropagation = tracePropagation;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(DomainEvent event) {
        var type = event.type();
        outboxEvents.save(OutboxEvent.record(type.aggregateType(), event.aggregateId(), type.eventName(),
                jsonMapper.writeValueAsString(event), CorrelationId.current(), tracePropagation.currentTraceParent()));
    }
}
