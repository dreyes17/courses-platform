# Notes for Claude

## Library documentation

- Check `.context7-docs/INDEX.md` first. It lists up-to-date docs already downloaded for Spring Boot 4,
  Spring AMQP, Spring Framework 7, Spring Security 7, Spring Data JPA, Testcontainers, Spring AI MCP,
  springdoc and Micrometer. Read the matching file, or search the folder with `grep -ri <term> .context7-docs`.
  The folder is git-ignored, so the Grep and Glob tools skip it.
- Call the `context7` MCP only when the cache doesn't cover the question. The shared quota is small, so
  batch what you need into one precise query.
- When a Context7 answer is broadly useful, add a line to `.devcontainer/context7/topics.txt` so the
  next `fetch-docs.py` run caches it.

## Verifying behaviour with MCP servers

When the app's `docker compose up` stack runs inside the devcontainer, prefer the MCP servers to ad-hoc curl:
`prometheus` for metrics (e.g. `courses_rate_limit_rejected_total`, `cache_gets_total`), `jaeger` for traces
(an enrollment should be one trace across outbox, RabbitMQ and consumers), `rabbitmq` for queues and DLQs,
`postgres` for data and query plans. `redis` sees the devcontainer's Redis, used by `./mvnw spring-boot:run` for
rate-limit buckets (`rate-limit:*`).

## Environment

See `.devcontainer/README.md`. Services: `postgres:5432`, `rabbitmq:5672` (UI on 15672), `redis:6379`; credentials `courses`/`courses`.
