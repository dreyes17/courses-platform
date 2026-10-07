package io.github.dreyes17.courses.messaging.inbox;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Must run in the same transaction as the consumer's side effects, so the dedup marker and the
 * effects commit or roll back together.
 */
@Component
public class IdempotentConsumer {

    private final ProcessedEventRepository processedEvents;

    public IdempotentConsumer(ProcessedEventRepository processedEvents) {
        this.processedEvents = processedEvents;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public boolean isFirstDelivery(UUID eventId, String consumerName) {
        return processedEvents.insertIfAbsent(eventId, consumerName) == 1;
    }
}
