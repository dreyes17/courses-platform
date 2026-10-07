package io.github.dreyes17.courses.messaging.events;

import java.time.Instant;
import java.util.UUID;

public record EnrollmentCompleted(UUID enrollmentId, UUID studentId, UUID courseId, Instant completedAt)
        implements DomainEvent {

    @Override
    public EventType type() {
        return EventType.ENROLLMENT_COMPLETED;
    }

    @Override
    public UUID aggregateId() {
        return enrollmentId;
    }
}
