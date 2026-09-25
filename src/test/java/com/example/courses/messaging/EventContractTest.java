package com.example.courses.messaging;

import com.example.courses.messaging.events.EnrollmentCreated;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Consumers depend on these field names; renaming one is a breaking change that needs a new event version. */
class EventContractTest {

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Test
    void enrollmentCreatedSerializesOnlyItsContractFields() {
        var event = new EnrollmentCreated(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                new BigDecimal("99.00"), "EUR", Instant.parse("2026-01-01T10:00:00Z"));

        String payload = jsonMapper.writeValueAsString(event);
        JsonNode json = jsonMapper.readTree(payload);

        assertThat(json.propertyNames()).containsExactlyInAnyOrder(
                "enrollmentId", "studentId", "courseId", "paymentId", "amount", "currency", "occurredAt");
        assertThat(payload).contains("\"amount\":99.00");
        assertThat(jsonMapper.readValue(payload, EnrollmentCreated.class)).isEqualTo(event);
    }
}
