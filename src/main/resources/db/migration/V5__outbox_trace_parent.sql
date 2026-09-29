-- W3C traceparent of the span that recorded the event ("00-" + 32-hex trace id + "-" + 16-hex span id + "-" +
-- 2-hex flags = 55 chars). The relay publishes later from another thread, so without it the trace would end at
-- the outbox instead of continuing through RabbitMQ into the consumers.
ALTER TABLE outbox_events ADD COLUMN trace_parent VARCHAR(55);
