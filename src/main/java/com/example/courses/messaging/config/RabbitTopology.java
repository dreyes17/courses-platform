package com.example.courses.messaging.config;

import com.example.courses.messaging.events.EventType;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarable;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Configuration(proxyBeanMethods = false)
public class RabbitTopology {

    public static final String EVENTS_EXCHANGE = "courses.events";
    public static final String DEAD_LETTER_EXCHANGE = "courses.events.dlx";

    public static final String PAYMENT_PROCESSING_QUEUE = "payments.enrollment-created";
    public static final String ENROLLMENT_ACTIVATION_QUEUE = "enrollments.payment-confirmed";
    public static final String ENROLLMENT_PAYMENT_FAILED_QUEUE = "enrollments.payment-failed";
    public static final String CERTIFICATE_ISSUING_QUEUE = "certificates.enrollment-completed";

    public static final String DLQ_SUFFIX = ".dlq";

    private static final Map<String, EventType> QUEUE_SUBSCRIPTIONS = Map.of(
            PAYMENT_PROCESSING_QUEUE, EventType.ENROLLMENT_CREATED,
            ENROLLMENT_ACTIVATION_QUEUE, EventType.PAYMENT_CONFIRMED,
            ENROLLMENT_PAYMENT_FAILED_QUEUE, EventType.PAYMENT_FAILED,
            CERTIFICATE_ISSUING_QUEUE, EventType.ENROLLMENT_COMPLETED);

    public static Set<String> consumerQueues() {
        return QUEUE_SUBSCRIPTIONS.keySet();
    }

    @Bean
    Declarables coursesTopology() {
        var events = new TopicExchange(EVENTS_EXCHANGE, true, false);
        var deadLetters = new DirectExchange(DEAD_LETTER_EXCHANGE, true, false);

        List<Declarable> declarables = new ArrayList<>(List.of(events, deadLetters));
        QUEUE_SUBSCRIPTIONS.forEach((queueName, eventType) -> {
            Queue queue = QueueBuilder.durable(queueName)
                    .deadLetterExchange(DEAD_LETTER_EXCHANGE)
                    .deadLetterRoutingKey(queueName + DLQ_SUFFIX)
                    .build();
            Queue deadLetterQueue = QueueBuilder.durable(queueName + DLQ_SUFFIX).build();
            Binding binding = BindingBuilder.bind(queue).to(events).with(eventType.routingKey());
            Binding deadLetterBinding = BindingBuilder.bind(deadLetterQueue).to(deadLetters).with(queueName + DLQ_SUFFIX);
            declarables.addAll(List.of(queue, deadLetterQueue, binding, deadLetterBinding));
        });
        return new Declarables(declarables);
    }
}
