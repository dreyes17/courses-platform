package com.example.courses.messaging;

import com.example.courses.AbstractIntegrationTest;
import com.example.courses.catalog.domain.Course;
import com.example.courses.certificate.repository.CertificateRepository;
import com.example.courses.enrollment.domain.Enrollment;
import com.example.courses.enrollment.domain.EnrollmentStatus;
import com.example.courses.enrollment.repository.EnrollmentRepository;
import com.example.courses.messaging.config.RabbitTopology;
import com.example.courses.messaging.events.DomainEvent;
import com.example.courses.messaging.events.EnrollmentCompleted;
import com.example.courses.messaging.events.PaymentConfirmed;
import com.example.courses.messaging.inbox.ProcessedEventId;
import com.example.courses.messaging.inbox.ProcessedEventRepository;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/** Publishes events straight to the broker to simulate redeliveries and poison messages. */
class ConsumerIdempotencyTest extends AbstractIntegrationTest {

    private static final Duration ASYNC_TIMEOUT = Duration.ofSeconds(20);

    @Autowired
    private RabbitTemplate rabbitTemplate;
    @Autowired
    private JsonMapper jsonMapper;
    @Autowired
    private EnrollmentRepository enrollments;
    @Autowired
    private CertificateRepository certificates;
    @Autowired
    private ProcessedEventRepository processedEvents;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void duplicatePaymentConfirmedActivatesTheEnrollmentOnlyOnce() {
        UUID enrollmentId = enrollmentInStatus(EnrollmentStatus.PENDING_PAYMENT);
        UUID eventId = UUID.randomUUID();
        var event = new PaymentConfirmed(UUID.randomUUID(), enrollmentId, Instant.now());

        publish(eventId, event);
        publish(eventId, event);

        await().atMost(ASYNC_TIMEOUT).untilAsserted(() -> {
            assertThat(enrollments.findById(enrollmentId).orElseThrow().getStatus())
                    .isEqualTo(EnrollmentStatus.ACTIVE);
            assertThat(queueDepth(RabbitTopology.ENROLLMENT_ACTIVATION_QUEUE)).isZero();
        });
        assertThat(processedEvents.existsById(new ProcessedEventId(eventId, "enrollment-activation"))).isTrue();
        assertStaysEmpty(RabbitTopology.ENROLLMENT_ACTIVATION_QUEUE + RabbitTopology.DLQ_SUFFIX);
    }

    @Test
    void redeliveredOrRepublishedEnrollmentCompletedIssuesASingleCertificate() {
        UUID enrollmentId = enrollmentInStatus(EnrollmentStatus.COMPLETED);
        var event = new EnrollmentCompleted(enrollmentId, UUID.randomUUID(), UUID.randomUUID(), Instant.now());
        UUID eventId = UUID.randomUUID();

        publish(eventId, event);
        publish(eventId, event);
        publish(UUID.randomUUID(), event);

        await().atMost(ASYNC_TIMEOUT).untilAsserted(() -> {
            assertThat(certificates.findByEnrollmentId(enrollmentId)).isPresent();
            assertThat(queueDepth(RabbitTopology.CERTIFICATE_ISSUING_QUEUE)).isZero();
        });
        assertStaysEmpty(RabbitTopology.CERTIFICATE_ISSUING_QUEUE + RabbitTopology.DLQ_SUFFIX);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from certificates where enrollment_id = ?", Integer.class, enrollmentId))
                .isEqualTo(1);
    }

    @Test
    void poisonMessageEndsInTheDeadLetterQueueInsteadOfBlockingTheQueue() {
        String dlq = RabbitTopology.ENROLLMENT_ACTIVATION_QUEUE + RabbitTopology.DLQ_SUFFIX;
        String messageId = UUID.randomUUID().toString();
        Message poison = MessageBuilder.withBody("{not json".getBytes(StandardCharsets.UTF_8))
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .setMessageId(messageId)
                .build();

        rabbitTemplate.send(RabbitTopology.EVENTS_EXCHANGE, "payment.confirmed", poison);

        Message deadLettered = rabbitTemplate.receive(dlq, ASYNC_TIMEOUT.toMillis());
        assertThat(deadLettered).isNotNull();
        assertThat(deadLettered.getMessageProperties().getMessageId()).isEqualTo(messageId);
    }

    private void publish(UUID eventId, DomainEvent event) {
        Message message = MessageBuilder.withBody(jsonMapper.writeValueAsBytes(event))
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .setMessageId(eventId.toString())
                .setType(event.type().eventName())
                .build();
        rabbitTemplate.send(RabbitTopology.EVENTS_EXCHANGE, event.type().routingKey(), message);
    }

    /** Retries finish well within the window, so a duplicate that failed would have reached the DLQ by then. */
    private void assertStaysEmpty(String queue) {
        await().during(Duration.ofSeconds(2)).atMost(Duration.ofSeconds(5))
                .until(() -> queueDepth(queue) == 0);
    }

    private long queueDepth(String queue) {
        var info = rabbitTemplate.execute(channel -> channel.queueDeclarePassive(queue));
        return info.getMessageCount();
    }

    /** Built directly through the domain, bypassing the outbox, so no real event races the test's own. */
    private UUID enrollmentInStatus(EnrollmentStatus target) {
        UUID courseId = publishedCourse(10, new BigDecimal("10.00"));
        UUID studentId = student();
        return transactionTemplate.execute(status -> {
            Course course = courses.findById(courseId).orElseThrow();
            Enrollment enrollment = Enrollment.requestFor(students.findById(studentId).orElseThrow(), course);
            if (target == EnrollmentStatus.COMPLETED) {
                enrollment.activate();
                enrollment.updateProgress(100);
            }
            return enrollments.save(enrollment).getId();
        });
    }
}
