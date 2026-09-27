package com.example.courses.web;

import com.example.courses.messaging.config.RabbitTopology;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class ObservabilityTest extends ApiTestSupport {

    private static final Duration ASYNC_TIMEOUT = Duration.ofSeconds(15);

    @Autowired
    private MeterRegistry meterRegistry;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Test
    void correlationIdFollowsTheFlowFromTheRequestThroughEveryConsumer() {
        String courseId = createPublishedCourse(createInstructor(), 5, BigDecimal.TEN);
        String correlationId = "it-" + UUID.randomUUID();

        MvcTestResult result = enrollWithCorrelationId(registerStudent().token(), courseId, correlationId);

        assertThat(result).hasStatus(HttpStatus.CREATED);
        assertThat(result).headers().hasValue("X-Correlation-Id", correlationId);
        // PaymentConfirmed is recorded by the payment consumer after receiving EnrollmentCreated over RabbitMQ,
        // so finding the id on it proves it crossed the outbox, the AMQP header and the consumer.
        await().atMost(ASYNC_TIMEOUT).untilAsserted(() -> assertThat(jdbcTemplate.queryForList(
                "select event_type from outbox_events where correlation_id = ?", String.class, correlationId))
                .contains("EnrollmentCreated", "PaymentConfirmed"));
    }

    @Test
    void unsafeCorrelationIdIsReplacedWithAGeneratedOne() {
        var result = mvc.get().uri("/api/categories")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken())
                .header("X-Correlation-Id", "<script>not a valid id</script>")
                .exchange();

        String echoed = result.getResponse().getHeader("X-Correlation-Id");
        assertThat(echoed).isNotNull().doesNotContain("<", " ");
        assertThat(UUID.fromString(echoed)).isNotNull();
    }

    @Test
    void enrollmentsAndPaymentsAreCountedByOutcome() {
        String courseId = createPublishedCourse(createInstructor(), 1, BigDecimal.TEN);
        double created = counter("courses.enrollments", "outcome", "created");
        double confirmed = counter("courses.payments.processed", "outcome", "confirmed");
        double rejectedFull = counter("courses.enrollments", "outcome", "course_full");

        assertThat(enroll(registerStudent().token(), courseId, UUID.randomUUID().toString()))
                .hasStatus(HttpStatus.CREATED);
        assertThat(enroll(registerStudent().token(), courseId, UUID.randomUUID().toString()))
                .hasStatus(HttpStatus.CONFLICT);

        assertThat(counter("courses.enrollments", "outcome", "created")).isEqualTo(created + 1);
        assertThat(counter("courses.enrollments", "outcome", "course_full")).isEqualTo(rejectedFull + 1);
        await().atMost(ASYNC_TIMEOUT).untilAsserted(() ->
                assertThat(counter("courses.payments.processed", "outcome", "confirmed"))
                        .isGreaterThanOrEqualTo(confirmed + 1));
    }

    @Test
    void deadLetterQueueDepthIsExposedAsAGauge() {
        String dlq = RabbitTopology.CERTIFICATE_ISSUING_QUEUE + RabbitTopology.DLQ_SUFFIX;
        rabbitTemplate.send("", dlq, MessageBuilder.withBody("parked".getBytes(StandardCharsets.UTF_8)).build());
        try {
            await().atMost(ASYNC_TIMEOUT).untilAsserted(() -> assertThat(meterRegistry
                    .get("courses.messaging.dlq.messages").tag("queue", dlq).gauge().value()).isEqualTo(1));
        } finally {
            rabbitTemplate.receive(dlq, 5_000);
        }
    }

    @Test
    void failedOutboxEventsAreExposedAsAGauge() {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into outbox_events (id, aggregate_type, aggregate_id, event_type, payload, status)
                values (?, 'Test', ?, 'TestEvent', '{}'::jsonb, 'FAILED')""", id, UUID.randomUUID());
        try {
            assertThat(meterRegistry.get("courses.outbox.events").tag("status", "failed").gauge().value())
                    .isGreaterThanOrEqualTo(1);
        } finally {
            jdbcTemplate.update("delete from outbox_events where id = ?", id);
        }
    }

    private MvcTestResult enrollWithCorrelationId(String token, String courseId, String correlationId) {
        return mvc.post().uri("/api/enrollments")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .header("X-Correlation-Id", correlationId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"courseId": "%s"}""".formatted(courseId))
                .exchange();
    }

    private double counter(String name, String... tags) {
        Counter counter = meterRegistry.find(name).tags(tags).counter();
        return counter == null ? 0 : counter.count();
    }
}
