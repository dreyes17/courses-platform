package io.github.dreyes17.courses.shared.observability;

import org.slf4j.MDC;

import java.util.UUID;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * Correlation id that follows one business flow across the HTTP request, the outbox and every RabbitMQ
 * consumer. It lives in the logging MDC, so each log line of the flow carries it.
 */
public final class CorrelationId {

    public static final String MDC_KEY = "correlationId";
    public static final String HTTP_HEADER = "X-Correlation-Id";
    public static final String AMQP_HEADER = "x-correlation-id";

    /** Client-supplied ids end up in logs, so anything outside this safe shape is replaced. */
    private static final Pattern SAFE = Pattern.compile("[A-Za-z0-9._:-]{1,100}");

    private CorrelationId() {
    }

    public static String current() {
        return MDC.get(MDC_KEY);
    }

    /** Returns the candidate if it is safe to log, otherwise a fresh id. */
    public static String sanitizeOrGenerate(String candidate) {
        return candidate != null && SAFE.matcher(candidate).matches() ? candidate : UUID.randomUUID().toString();
    }

    /**
     * Runs the action with the given id in the MDC, restoring whatever was there before. A null id runs the
     * action unchanged.
     */
    public static <T> T callWith(String correlationId, Supplier<T> action) {
        if (correlationId == null) {
            return action.get();
        }
        String previous = MDC.get(MDC_KEY);
        MDC.put(MDC_KEY, correlationId);
        try {
            return action.get();
        } finally {
            if (previous == null) {
                MDC.remove(MDC_KEY);
            } else {
                MDC.put(MDC_KEY, previous);
            }
        }
    }
}
