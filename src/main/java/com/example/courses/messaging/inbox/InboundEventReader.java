package com.example.courses.messaging.inbox;

import com.example.courses.messaging.events.DomainEvent;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.util.UUID;

@Component
public class InboundEventReader {

    private final JsonMapper jsonMapper;

    public InboundEventReader(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    /** Malformed messages can never succeed, so they are rejected straight to the dead-letter queue. */
    public <T extends DomainEvent> InboundEvent<T> read(Message message, Class<T> payloadType) {
        String messageId = message.getMessageProperties().getMessageId();
        if (messageId == null) {
            throw new AmqpRejectAndDontRequeueException("%s message has no messageId".formatted(
                    payloadType.getSimpleName()));
        }
        try {
            UUID eventId = UUID.fromString(messageId);
            return new InboundEvent<>(eventId, jsonMapper.readValue(message.getBody(), payloadType));
        } catch (IllegalArgumentException | JacksonException e) {
            throw new AmqpRejectAndDontRequeueException("Malformed %s message %s".formatted(
                    payloadType.getSimpleName(), messageId), e);
        }
    }
}
