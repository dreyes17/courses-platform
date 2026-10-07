package io.github.dreyes17.courses.certificate.messaging;

import io.github.dreyes17.courses.certificate.application.CertificateIssuer;
import io.github.dreyes17.courses.messaging.config.RabbitTopology;
import io.github.dreyes17.courses.messaging.events.EnrollmentCompleted;
import io.github.dreyes17.courses.messaging.inbox.InboundEventReader;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
class EnrollmentCompletedListener {

    private final InboundEventReader reader;
    private final CertificateIssuer certificateIssuer;

    EnrollmentCompletedListener(InboundEventReader reader, CertificateIssuer certificateIssuer) {
        this.reader = reader;
        this.certificateIssuer = certificateIssuer;
    }

    @RabbitListener(queues = RabbitTopology.CERTIFICATE_ISSUING_QUEUE)
    void onEnrollmentCompleted(Message message) {
        reader.consume(message, EnrollmentCompleted.class,
                event -> certificateIssuer.onEnrollmentCompleted(event.eventId(), event.payload()));
    }
}
