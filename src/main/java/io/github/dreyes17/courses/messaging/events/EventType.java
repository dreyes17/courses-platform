package io.github.dreyes17.courses.messaging.events;

import java.util.Arrays;

public enum EventType {

    ENROLLMENT_CREATED("EnrollmentCreated", "enrollment.created", "Enrollment", EnrollmentCreated.class),
    PAYMENT_CONFIRMED("PaymentConfirmed", "payment.confirmed", "Payment", PaymentConfirmed.class),
    PAYMENT_FAILED("PaymentFailed", "payment.failed", "Payment", PaymentFailed.class),
    ENROLLMENT_COMPLETED("EnrollmentCompleted", "enrollment.completed", "Enrollment", EnrollmentCompleted.class);

    public static final int CURRENT_VERSION = 1;

    private final String eventName;
    private final String routingKey;
    private final String aggregateType;
    private final Class<? extends DomainEvent> payloadType;

    EventType(String eventName, String routingKey, String aggregateType, Class<? extends DomainEvent> payloadType) {
        this.eventName = eventName;
        this.routingKey = routingKey;
        this.aggregateType = aggregateType;
        this.payloadType = payloadType;
    }

    public static EventType fromEventName(String eventName) {
        return Arrays.stream(values())
                .filter(type -> type.eventName.equals(eventName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown event type: " + eventName));
    }

    public String eventName() {
        return eventName;
    }

    public String routingKey() {
        return routingKey;
    }

    public String aggregateType() {
        return aggregateType;
    }

    public Class<? extends DomainEvent> payloadType() {
        return payloadType;
    }
}
