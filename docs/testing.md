# Testing and CI

- [Test suite](#test-suite)
- [Proving the tests catch regressions](#proving-the-tests-catch-regressions)
- [Continuous integration](#continuous-integration)

## Test suite

`./mvnw test` runs all 149 tests in under a minute. Almost every integration test shares a single Spring context
and a single pair of containers (`AbstractIntegrationTest`), which is why the suite is fast despite using real
PostgreSQL and RabbitMQ. There are two other contexts:

- `@RealServerTest`, with real servers on real ports. Shared by `ManagementPortTest`, `VirtualThreadsTest` and
  `McpServerTest`.
- The one in `RateLimitingTest`, which turns rate limiting on with low limits. It's off in the shared context,
  because the suite performs hundreds of logins from the same address.

| Level | Covers | Classes |
|---|---|---|
| **Domain unit** | State machines and invariants of `Course` and `Enrollment`, without Spring or mocks | `CourseTest`, `EnrollmentTest` |
| **Use-case unit** (Mockito) | Error branches and what must *not* happen: no seat consumed if already enrolled, no charge on a cancelled enrollment, no reactivating a cancelled one, no second certificate, duplicate deliveries without effects, login that doesn't reveal which emails exist | `EnrollmentServiceTest`, `PaymentProcessorTest`, `PaymentOutcomeHandlerTest`, `CertificateIssuerTest`, `IdempotentRequestsTest`, `AccountServiceTest` |
| **Mapping unit** | Each nested or derived view field comes from its correct source | `CatalogViewMapperTest`, `EnrollmentViewMapperTest` |
| **Integration** (Testcontainers) | Concurrency on capacity (20 threads, 3 seats), full flow over RabbitMQ, consumer idempotency with duplicate deliveries, poison message → DLQ without retries, message that fails processing → retries with backoff → DLQ, `Idempotency-Key` | `EnrollmentConcurrencyTest`, `EnrollmentFlowTest`, `ConsumerIdempotencyTest` |
| **HTTP** (MockMvc) | End-to-end flow through the API with each role, error mapping to `problem+json`, 401/403 and ownership rules, drafts visible only to ADMIN and their instructor, deleting an instructor together with their account, list filters (including the ADMIN-only global enrollment list), no N+1 (also with filters), payment visible with the decline reason, certificate visible only to the right people and verifiable without a token and without exposing ids or email | `EnrollmentApiTest`, `ErrorHandlingApiTest`, `SecurityApiTest`, `ListFilteringApiTest`, `QueryEfficiencyTest`, `CertificateAndPaymentApiTest` |
| **API documentation** | Every operation in the OpenAPI spec has its real success code and all its errors as `ProblemDetail` | `ApiDocumentationTest` |
| **Real ports** | Actuator reachable without credentials only on the management port, health and readiness with database and broker, `info` with build and Java, API protected and absent on that port | `ManagementPortTest` |
| **Virtual threads** | HTTP requests, RabbitMQ consumers and scheduled tasks run on virtual threads | `VirtualThreadsTest` |
| **Observability** | `correlationId` and OpenTelemetry trace propagated from the HTTP request to the consumer through the outbox and RabbitMQ, unsafe ids replaced, counters by outcome, DLQ and `FAILED` outbox gauges | `ObservabilityTest` |
| **Cache** | Reads served from cache, eviction on every write (seats included), permissions applied on hits too, metrics | `CatalogCacheTest` |
| **Cursor pagination** | Full walk without repeating or skipping courses, course created mid-walk, only `PUBLISHED` for students, invalid cursor, max size | `CursorPaginationTest` |
| **Rate limiting** | Login limited by IP (429 with `Retry-After`, metric, another IP unaffected), enrollment and MCP limited by user and not IP, remaining budget; with real Redis: two instances share a budget, an unreachable Redis fails fast and the filter lets the request through and counts it | `RateLimitingTest`, `RedisRateLimitingTest` |
| **MCP** | All 32 tools with description and parameters; catalog management (categories, courses and instructors) and deletions; full flow through tools: enrollment with idempotent retry, asynchronous activation, payment, certificate and its verification; same permissions and draft visibility as the API; validation; errors without internal details; 401 without a token | `McpServerTest`, `McpToolErrorsTest` |
| **Contract** | JSON fields of published events | `EventContractTest` |

## Proving the tests catch regressions

To make sure the tests don't pass by accident, I deliberately removed eight safeguards and confirmed the tests
failed:

- without the `@EntityGraph`, `QueryEfficiencyTest` detects the N+1;
- without the state check in `PaymentProcessor`, `PaymentProcessorTest` detects that a cancelled enrollment is
  charged;
- without the `x-correlation-id` header in `OutboxRelay`, `ObservabilityTest` detects that the id doesn't reach the
  consumer;
- with `spring.threads.virtual.enabled=false`, all three tests in `VirtualThreadsTest` fail;
- without the `@CacheEvict` on `tryReserveSeat`/`releaseSeat`, `CatalogCacheTest` detects that the course still
  shows 2 free seats after an enrollment;
- without registering the rate-limit filter, the `RateLimitingTest` tests fail;
- without the `@PreAuthorize` on `create_category`, `McpServerTest` detects that a student can create categories;
- without the trace continuation in the outbox relay, `ObservabilityTest` detects that `PaymentConfirmed` ends up in
  a different trace.

## Continuous integration

`.github/workflows/ci.yml` runs on every push to `main`, on every pull request and on demand. It has two parallel
jobs:

| Job | What it does |
|---|---|
| Build and test | `./mvnw -B verify` on Java 21 (Temurin): compiles and runs all 149 tests. Integration tests use the Docker that `ubuntu-latest` runners already provide, so Testcontainers works with no setup. On failure, it uploads the Surefire reports as an artifact. |
| Docker image and deployment config | Builds the `Dockerfile` image, validates `docker-compose.yml` against `.env.example`, validates the Grafana dashboard JSON and validates the Prometheus config and alerts with `promtool`. |

- It reuses Maven dependencies across runs (`setup-java` cache), has read-only permissions and cancels the previous
  run on the same branch when a new push arrives.
- The workflow passes `actionlint` with no warnings, and each of its steps was run locally with the same command.
