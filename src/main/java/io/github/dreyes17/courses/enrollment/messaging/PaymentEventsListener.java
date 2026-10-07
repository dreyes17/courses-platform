package io.github.dreyes17.courses.enrollment.messaging;

import io.github.dreyes17.courses.enrollment.application.PaymentOutcomeHandler;
import io.github.dreyes17.courses.messaging.config.RabbitTopology;
import io.github.dreyes17.courses.messaging.events.PaymentConfirmed;
import io.github.dreyes17.courses.messaging.events.PaymentFailed;
import io.github.dreyes17.courses.messaging.inbox.InboundEventReader;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
class PaymentEventsListener {

    private final InboundEventReader reader;
    private final PaymentOutcomeHandler paymentOutcomes;

    PaymentEventsListener(InboundEventReader reader, PaymentOutcomeHandler paymentOutcomes) {
        this.reader = reader;
        this.paymentOutcomes = paymentOutcomes;
    }

    @RabbitListener(queues = RabbitTopology.ENROLLMENT_ACTIVATION_QUEUE)
    void onPaymentConfirmed(Message message) {
        reader.consume(message, PaymentConfirmed.class,
                event -> paymentOutcomes.onPaymentConfirmed(event.eventId(), event.payload()));
    }

    @RabbitListener(queues = RabbitTopology.ENROLLMENT_PAYMENT_FAILED_QUEUE)
    void onPaymentFailed(Message message) {
        reader.consume(message, PaymentFailed.class,
                event -> paymentOutcomes.onPaymentFailed(event.eventId(), event.payload()));
    }
}
