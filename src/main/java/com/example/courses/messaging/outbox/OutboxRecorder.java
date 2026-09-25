package com.example.courses.messaging.outbox;

import com.example.courses.messaging.events.DomainEvent;
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

    public OutboxRecorder(OutboxEventRepository outboxEvents, JsonMapper jsonMapper) {
        this.outboxEvents = outboxEvents;
        this.jsonMapper = jsonMapper;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(DomainEvent event) {
        var type = event.type();
        outboxEvents.save(OutboxEvent.record(
                type.aggregateType(), event.aggregateId(), type.eventName(), jsonMapper.writeValueAsString(event)));
    }
}
