package com.example.courses.shared.observability;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Carries a trace across the transactional outbox. Automatic propagation can't: the event is published later and
 * from the relay's thread, long after the request's span has ended. So the W3C {@code traceparent} of the span
 * that recorded the event is stored with it, and the relay publishes inside a span continuing that trace; the
 * observed RabbitTemplate then propagates it to the consumer as usual. The same idea as {@link CorrelationId}.
 */
@Component
public class TracePropagation {

    private static final String TRACEPARENT = "traceparent";

    private final Tracer tracer;
    private final Propagator propagator;

    public TracePropagation(ObjectProvider<Tracer> tracer, ObjectProvider<Propagator> propagator) {
        this.tracer = tracer.getIfAvailable(() -> Tracer.NOOP);
        this.propagator = propagator.getIfAvailable(() -> Propagator.NOOP);
    }

    /** The W3C traceparent of the current span, or null when nothing is being traced. */
    public String currentTraceParent() {
        TraceContext context = tracer.currentTraceContext().context();
        if (context == null) {
            return null;
        }
        Map<String, String> carrier = new HashMap<>();
        propagator.inject(context, carrier, Map::put);
        return carrier.get(TRACEPARENT);
    }

    /** Runs {@code work} inside a new span that continues the given trace (or untraced, if there is none). */
    public <T> T continueTrace(String traceParent, String spanName, Supplier<T> work) {
        if (traceParent == null) {
            return work.get();
        }
        Span span = propagator.extract(Map.of(TRACEPARENT, traceParent), Map::get).name(spanName).start();
        try (Tracer.SpanInScope ignored = tracer.withSpan(span)) {
            return work.get();
        } finally {
            span.end();
        }
    }
}
