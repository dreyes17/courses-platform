package com.example.courses.messaging.outbox;

import com.example.courses.messaging.config.RabbitTopology;
import com.example.courses.messaging.events.EventType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;
import java.util.concurrent.TimeUnit;

/**
 * Publishes pending outbox rows. A row is marked PUBLISHED only after the broker confirms it, so a
 * crash in between re-publishes it on the next run: delivery is at-least-once and consumers dedupe.
 */
@Component
public class OutboxRelay {

    public static final String EVENT_VERSION_HEADER = "x-event-version";

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxEventRepository outboxEvents;
    private final RabbitTemplate rabbitTemplate;
    private final TransactionTemplate transactionTemplate;
    private final int batchSize;
    private final Duration confirmTimeout;

    public OutboxRelay(OutboxEventRepository outboxEvents, RabbitTemplate rabbitTemplate,
                       TransactionTemplate transactionTemplate,
                       @Value("${app.outbox.batch-size:100}") int batchSize,
                       @Value("${app.outbox.confirm-timeout:5s}") Duration confirmTimeout) {
        this.outboxEvents = outboxEvents;
        this.rabbitTemplate = rabbitTemplate;
        this.transactionTemplate = transactionTemplate;
        this.batchSize = batchSize;
        this.confirmTimeout = confirmTimeout;
    }

    @Scheduled(fixedDelayString = "${app.outbox.poll-interval:500ms}")
    public void publishPending() {
        transactionTemplate.executeWithoutResult(status -> {
            for (OutboxEvent event : outboxEvents.lockNextPending(batchSize)) {
                if (!publish(event)) {
                    return;
                }
            }
        });
    }

    /** Returns false when the broker is unavailable, to stop the batch and retry on the next run. */
    private boolean publish(OutboxEvent event) {
        EventType type = EventType.fromEventName(event.getEventType());
        var correlation = new CorrelationData(event.getId().toString());
        try {
            rabbitTemplate.send(RabbitTopology.EVENTS_EXCHANGE, type.routingKey(), toMessage(event), correlation);
            var confirm = correlation.getFuture().get(confirmTimeout.toMillis(), TimeUnit.MILLISECONDS);
            if (correlation.getReturned() != null) {
                log.error("Outbox event {} ({}) is unroutable; marking it FAILED", event.getId(), type.eventName());
                event.markFailed();
                return true;
            }
            if (!confirm.ack()) {
                log.warn("Broker nacked outbox event {}: {}", event.getId(), confirm.reason());
                return false;
            }
            event.markPublished();
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception e) {
            log.warn("Could not publish outbox event {}; will retry", event.getId(), e);
            return false;
        }
    }

    private static Message toMessage(OutboxEvent event) {
        return MessageBuilder.withBody(event.getPayload().getBytes(StandardCharsets.UTF_8))
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .setContentEncoding(StandardCharsets.UTF_8.name())
                .setMessageId(event.getId().toString())
                .setType(event.getEventType())
                .setCorrelationId(event.getAggregateId().toString())
                .setTimestamp(Date.from(event.getCreatedAt()))
                .setHeader(EVENT_VERSION_HEADER, EventType.CURRENT_VERSION)
                .setHeader("x-aggregate-type", event.getAggregateType())
                .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                .build();
    }
}
