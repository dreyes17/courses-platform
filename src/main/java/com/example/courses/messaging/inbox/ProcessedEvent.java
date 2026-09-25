package com.example.courses.messaging.inbox;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "processed_events")
public class ProcessedEvent {

    @EmbeddedId
    private ProcessedEventId id;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    protected ProcessedEvent() {
    }

    private ProcessedEvent(ProcessedEventId id) {
        this.id = Objects.requireNonNull(id, "id");
        this.processedAt = Instant.now();
    }

    public static ProcessedEvent of(UUID eventId, String consumerName) {
        return new ProcessedEvent(new ProcessedEventId(eventId, consumerName));
    }

    public ProcessedEventId getId() {
        return id;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }
}
