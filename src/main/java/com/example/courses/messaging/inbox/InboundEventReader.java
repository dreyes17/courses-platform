package com.example.courses.messaging.inbox;

import com.example.courses.messaging.events.DomainEvent;
import com.example.courses.shared.observability.CorrelationId;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.util.UUID;
import java.util.function.Consumer;

@Component
public class InboundEventReader {

    private final JsonMapper jsonMapper;

    public InboundEventReader(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    /**
     * Parses the message and hands it to the handler with the producer's correlation id restored in the MDC,
     * so the consumer's logs and any events it records continue the same flow.
     */
    public <T extends DomainEvent> void consume(Message message, Class<T> payloadType,
                                                Consumer<InboundEvent<T>> handler) {
        InboundEvent<T> event = read(message, payloadType);
        Object header = message.getMessageProperties().getHeaders().get(CorrelationId.AMQP_HEADER);
        String correlationId = CorrelationId.sanitizeOrGenerate(
                header != null ? header.toString() : event.eventId().toString());
        CorrelationId.callWith(correlationId, () -> {
            handler.accept(event);
            return null;
        });
    }

    /** Malformed messages can never succeed, so they are rejected straight to the dead-letter queue. */
    private <T extends DomainEvent> InboundEvent<T> read(Message message, Class<T> payloadType) {
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
