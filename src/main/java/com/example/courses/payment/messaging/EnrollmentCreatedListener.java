package com.example.courses.payment.messaging;

import com.example.courses.messaging.config.RabbitTopology;
import com.example.courses.messaging.events.EnrollmentCreated;
import com.example.courses.messaging.inbox.InboundEventReader;
import com.example.courses.payment.application.PaymentProcessor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
class EnrollmentCreatedListener {

    private final InboundEventReader reader;
    private final PaymentProcessor paymentProcessor;

    EnrollmentCreatedListener(InboundEventReader reader, PaymentProcessor paymentProcessor) {
        this.reader = reader;
        this.paymentProcessor = paymentProcessor;
    }

    @RabbitListener(queues = RabbitTopology.PAYMENT_PROCESSING_QUEUE)
    void onEnrollmentCreated(Message message) {
        reader.consume(message, EnrollmentCreated.class,
                event -> paymentProcessor.process(event.eventId(), event.payload()));
    }
}
