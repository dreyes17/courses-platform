package com.example.courses.certificate.messaging;

import com.example.courses.certificate.application.CertificateIssuer;
import com.example.courses.messaging.config.RabbitTopology;
import com.example.courses.messaging.events.EnrollmentCompleted;
import com.example.courses.messaging.inbox.InboundEventReader;
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
        var event = reader.read(message, EnrollmentCompleted.class);
        certificateIssuer.onEnrollmentCompleted(event.eventId(), event.payload());
    }
}
