package io.github.dreyes17.courses.messaging.config;

import io.github.dreyes17.courses.messaging.outbox.OutboxEventRepository;
import io.github.dreyes17.courses.messaging.outbox.OutboxStatus;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.QueueInformation;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.function.Supplier;

/**
 * Operational gauges evaluated on each metrics scrape: messages parked in every DLQ, and outbox rows still
 * pending or FAILED (the latter need an operator). An unreachable broker or database reports NaN instead of
 * breaking the whole scrape.
 */
@Component
class MessagingGauges {

    private static final Logger log = LoggerFactory.getLogger(MessagingGauges.class);

    MessagingGauges(MeterRegistry registry, AmqpAdmin amqpAdmin, OutboxEventRepository outboxEvents) {
        for (String queue : RabbitTopology.consumerQueues()) {
            String deadLetterQueue = queue + RabbitTopology.DLQ_SUFFIX;
            Gauge.builder("courses.messaging.dlq.messages",
                            safely(() -> messageCount(amqpAdmin, deadLetterQueue)))
                    .description("Messages waiting in a dead-letter queue")
                    .tag("queue", deadLetterQueue)
                    .register(registry);
        }
        for (OutboxStatus status : new OutboxStatus[] {OutboxStatus.PENDING, OutboxStatus.FAILED}) {
            Gauge.builder("courses.outbox.events", safely(() -> outboxEvents.countByStatus(status)))
                    .description("Outbox events by status; FAILED ones need manual attention")
                    .tag("status", status.name().toLowerCase(Locale.ROOT))
                    .register(registry);
        }
    }

    private static long messageCount(AmqpAdmin amqpAdmin, String queue) {
        QueueInformation info = amqpAdmin.getQueueInfo(queue);
        return info == null ? 0 : info.getMessageCount();
    }

    private static Supplier<Number> safely(Supplier<Number> value) {
        return () -> {
            try {
                return value.get();
            } catch (RuntimeException e) {
                log.debug("Gauge value unavailable", e);
                return Double.NaN;
            }
        };
    }
}
