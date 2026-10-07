# Observability

- [Metrics](#metrics-micrometer--actuatorprometheus)
- [Prometheus and alerts](#prometheus-and-alerts)
- [Correlated logs](#correlated-logs)
- [Distributed tracing](#distributed-tracing-opentelemetry--jaeger)
- [Dashboard](#dashboard-grafana)

## Metrics (Micrometer → `/actuator/prometheus`)

On top of Spring Boot's automatic metrics (JVM, HTTP, connection pool, RabbitMQ), the app exports these business
and operational metrics:

| Metric (Prometheus name) | Type | Measures |
|---|---|---|
| `courses_enrollments_total{outcome}` | counter | Enrollment attempts: `created`, `course_full`, `already_enrolled` |
| `courses_payments_processed_total{outcome}` | counter | Payments processed: `confirmed`, `failed` |
| `courses_certificates_issued_total` | counter | Certificates issued |
| `courses_messaging_dlq_messages{queue}` | gauge | Messages waiting in each DLQ |
| `courses_outbox_events{status}` | gauge | Outbox events `pending` (publishing lag) and `failed` (need intervention) |
| `courses_rate_limit_rejected_total{rule}` | counter | Requests rejected with 429, by rule (see [Rate limiting](security.md#rate-limiting)) |
| `cache_gets_total{cache,result}` | counter | Reads of each catalog cache, `hit` or `miss` (see [Catalog cache](performance.md#catalog-cache)) |

- **Only what commits is counted.** Created enrollments, payments and certificates are incremented after the
  *commit*. An operation that rolls back doesn't inflate the metric, and a retry after a rollback doesn't count
  twice. Rejections are counted immediately: the rejection is itself the outcome.
- **Every series exists from startup**, at 0. A panel or alert never runs into a series that "doesn't exist
  yet".
- **Gauges are computed on each scrape:** they ask the broker (each DLQ's depth) and the database (count by
  status over an existing index). If one doesn't respond, the gauge is `NaN` instead of breaking the export of
  everything else.

## Prometheus and alerts

`docker compose up` also starts Prometheus 3.15, configured in `observability/prometheus/`. It scrapes
`http://app:8081/actuator/prometheus` every 15 seconds over the internal network and evaluates these rules
(`alerts.yml`):

| Alert | Condition | Severity | Meaning |
|---|---|---|---|
| `CoursesInstanceDown` | `up == 0` for 1 min | critical | Prometheus can't scrape the app |
| `MessagesInDeadLetterQueue` | `courses_messaging_dlq_messages > 0` for 1 min | warning | A consumer exhausted its retries. Inspect the messages and republish or discard them |
| `OutboxEventsFailed` | `courses_outbox_events{status="failed"} > 0` | critical | Events the relay gave up on; they need manual intervention |
| `RateLimitingUnavailable` | some request passed unchecked in the last 5 min | warning | Redis isn't responding and rate limiting isn't being enforced (see [Rate limiting](security.md#rate-limiting)) |
| `OutboxPublishingStalled` | `pending` doesn't drop below 50 in 5 min | warning | The relay drains the outbox every 500 ms, so a sustained backlog means it can't publish |

- **The UI is at <http://localhost:9090> and bound to `127.0.0.1` only.** It shows every metric, which the app
  deliberately keeps off the public network (see
  [Actuator and the management port](security.md#actuator-and-the-management-port)).
- **Verified end to end** on the Compose stack: the `app:8081` target shows `up`, business and cache metrics
  arrive, and all five rules load without errors. After leaving a message in
  `certificates.enrollment-completed.dlq`, `MessagesInDeadLetterQueue` went to `firing` once the minute passed.
- **CI validates the config and the rules** with `promtool` (see [Continuous integration](testing.md#continuous-integration)).
- Alertmanager, which would decide who gets notified and how, is out of scope.

## Correlated logs

Every flow carries a **`correlationId`** end to end, across RabbitMQ too:

1. `CorrelationIdFilter` takes it from the request's `X-Correlation-Id` header (or generates one), puts it in
   the logging MDC and returns it in the response. It runs before Spring Security, so even 401/403s are
   correlated.
2. `OutboxRecorder` stores it in the outbox row (`V3__outbox_correlation_id.sql`), and `OutboxRelay` sends it
   as the AMQP header `x-correlation-id`.
3. Each consumer restores it into the MDC while processing the message (`InboundEventReader.consume`), so its
   logs carry the same id. Events it emits inherit it, and the id keeps travelling to the next step.

So one `grep` for an id returns the HTTP request, the charge, the activation and the certificate of a single
enrollment. `ObservabilityTest` checks it: it sends an enrollment with its id and verifies that the
`PaymentConfirmed` recorded by the payment consumer carries that same id.

A client-supplied id is only accepted if it has a safe format (`[A-Za-z0-9._:-]{1,100}`); otherwise it's replaced
by a generated one. Nobody can inject line breaks or arbitrary text into the logs.

**Format.** In Compose, logs are **JSON** (ECS format, `LOGGING_STRUCTURED_FORMAT_CONSOLE=ecs`), one line per
event with `correlationId`, `traceId` and `spanId` as fields, ready for a log collector. In local development
they're plain text, with those ids in brackets. For example:

```json
{"@timestamp":"…","log":{"level":"INFO","logger":"…PaymentProcessor"},"process":{"thread":{"name":"rabbit-simple-3"}},"correlationId":"demo-23643","message":"Payment … confirmed (transaction …)", …}
```

**The correlation id stays alongside traces** (see below): it doesn't depend on sampling, so it's in every log
and event, and it's what the client sees in `X-Correlation-Id`.

## Distributed tracing (OpenTelemetry + Jaeger)

Micrometer Tracing with the OpenTelemetry bridge. In Compose, every request is traced and exported over OTLP to
Jaeger (<http://localhost:16686>, local machine only). An enrollment shows up as **a single trace**, from the
HTTP request to the last consumer:

```
POST /api/enrollments                                  HTTP request (with security and @PreAuthorize)
└─ outbox publish EnrollmentCreated                    outbox relay, continuing the request's trace
   └─ courses.events/enrollment.created send
      └─ payments.enrollment-created receive           payment consumer
         └─ outbox publish PaymentConfirmed
            └─ courses.events/payment.confirmed send
               └─ enrollments.payment-confirmed receive  enrollment activation
```

**The problem to solve** is the same as with the correlation id. Automatic propagation doesn't cross the outbox:
the event is published later, from the relay's thread, after the request has finished, so the trace would break
right before RabbitMQ. It's solved the same way as the correlation id:

1. `OutboxRecorder` stores the current span's W3C `traceparent` with the event (`V5__outbox_trace_parent.sql`).
2. `OutboxRelay` publishes each event inside a span that continues that trace (`TracePropagation`).
3. With Spring AMQP observation enabled, `RabbitTemplate` sends the context in the message headers and each
   listener continues it. Events emitted by a consumer store their own `traceparent` in turn, and the chain
   continues.

**Verified.** On the Compose stack, an enrollment's trace gathers all 13 spans: the request and its security, two
relay publishes, two sends and two receives. `ObservabilityTest` checks it without Jaeger: it sends an enrollment
with a known `traceparent` and verifies that the `PaymentConfirmed` recorded by the payment consumer carries the
same trace id. Without the continuation in the relay, the test fails.

- **Sampling:** 10% by default (`TRACING_SAMPLING_PROBABILITY`), so it's cheap in production. Compose raises it
  to 100%.
- **Export:** only if `MANAGEMENT_OPENTELEMETRY_TRACING_EXPORT_OTLP_ENDPOINT` is set, as Compose does. Nothing is
  exported in local development or tests.
- **Logs:** every line carries `traceId` and `spanId` besides `correlationId`, so a log line leads to its trace.

## Dashboard (Grafana)

Grafana (<http://localhost:3000>, local machine only) starts with Prometheus as its data source and the
**Courses — platform** dashboard provisioned from `observability/grafana/` as the home page. It's viewable
without logging in; editing needs the `admin` account with `GRAFANA_ADMIN_PASSWORD`.

| Row | Panels |
|---|---|
| Status | instances up, messages in DLQs, `FAILED` and pending outbox events; they change colour when they need attention |
| HTTP traffic | requests per second by status code, p95 latency per endpoint, rate-limit rejections |
| Business | enrollments by outcome, confirmed and failed payments, certificates issued |
| Messaging, outbox and cache | messages in each DLQ, outbox events by status, hit rate of each cache |
| Resources | Hikari connections (active, pending, max), JVM threads, heap memory |

- p95 is computed from the latency histograms the app exports (`percentiles-histogram` for
  `http.server.requests`), so it stays correct when aggregating several instances.
- The Hikari panel completes the virtual-threads story: with them, the real limit is the connection pool, and a
  sustained "pending" value signals saturation.
- Verified on the Compose stack: after generating traffic, every dashboard query returns data.
- Traces are browsed in the Jaeger UI, linked from the dashboard. They aren't integrated into Grafana because
  Jaeger 2 only serves its v3 query API, which Grafana 13's Jaeger data source doesn't use.
