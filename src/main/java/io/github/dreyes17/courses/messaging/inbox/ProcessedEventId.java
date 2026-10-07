package io.github.dreyes17.courses.messaging.inbox;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.UUID;

@Embeddable
public record ProcessedEventId(
        @Column(name = "event_id", nullable = false) UUID eventId,
        @Column(name = "consumer_name", nullable = false, length = 100) String consumerName
) implements Serializable {
}
