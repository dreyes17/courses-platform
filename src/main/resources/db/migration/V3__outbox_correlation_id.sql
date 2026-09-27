-- Carries the correlation id of the request that produced the event, so consumers can continue it.
ALTER TABLE outbox_events ADD COLUMN correlation_id VARCHAR(100);
