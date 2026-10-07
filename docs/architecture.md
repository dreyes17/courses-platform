# Architecture

- [Package layout](#package-layout)
- [Separation of concerns](#separation-of-concerns)
- [Why layered and not hexagonal](#why-layered-and-not-hexagonal)
- [Entity → view mapping (MapStruct)](#entity--view-mapping-mapstruct)
- [Invariants enforced by the database](#invariants-enforced-by-the-database)

## Package layout

A **layered architecture organised by context** (*package-by-feature*): code is grouped first by bounded
context and then, inside each one, by layer. In every context, `domain` holds the entities and their
invariants, `repository` the data access, `application` the use cases (they own the transactions and return
`record` views, never entities), `web` the REST controllers, `messaging` the RabbitMQ listeners and `mcp` the
MCP server tools. Controllers, listeners and tools are thin adapters: they validate or read the input, delegate
to `application` and translate the result.

| Package | Responsibility |
|---|---|
| `catalog` | Categories, instructors and courses — CRUD, publish/archive, composable search (`CourseSpecifications`) and atomic seat reservation (`CourseRepository.tryReserveSeat`) |
| `enrollment` | Students and enrollments — state machine `PENDING_PAYMENT → ACTIVE → COMPLETED` / `CANCELLED`; `EnrollmentService` (enroll, progress, cancel, listings) and `PaymentOutcomeHandler` (reacts to `PaymentConfirmed`/`PaymentFailed`) |
| `payment` | Payments — `PENDING → CONFIRMED` / `FAILED`; `PaymentProcessor` consumes `EnrollmentCreated` and charges a simulated gateway; `PaymentService` exposes an enrollment's payment, with the reason if it failed |
| `certificate` | `CertificateIssuer` consumes `EnrollmentCompleted` and issues the certificate; `CertificateService` exposes it to its student and lets anyone verify a code |
| `messaging.events` | Event contract: `sealed interface DomainEvent` + one `record` per event, and `EventType` (name, routing key, version) |
| `messaging.outbox` | `OutboxRecorder` (writes events in the business transaction) and `OutboxRelay` (publishes them to RabbitMQ) |
| `messaging.inbox` | Consumer-side deduplication (`processed_events`) and reading of incoming messages |
| `messaging.config` | RabbitMQ topology declared in code |
| `idempotency` | HTTP-level idempotency for the `Idempotency-Key` header |
| `identity` | User accounts (`users`), student sign-up, instructor onboarding, login and JWT issuing |
| `shared` | `BaseEntity`, domain exception hierarchy, `GlobalExceptionHandler` (RFC 9457 errors), `PageResponse`, `MappingConfig` (MapStruct), `CacheConfig` (catalog cache), OpenAPI and `shared.security` (filters, JWT, access rules) |

## Separation of concerns

| Concern | Where | How |
|---|---|---|
| Thin REST controllers | `<context>.web` (`CourseController`, `EnrollmentController`...) | Validate input with Jakarta Validation, apply `@PreAuthorize`, delegate to `application` and translate to HTTP (`201` + `Location`, `204`...). No business rules. |
| Services / use cases | `<context>.application` (`EnrollmentService`, `CourseService`, `PaymentProcessor`...) | Orchestrate the use case and own the transactions (`@Transactional`). State rules live in the entities. |
| Repositories | `<context>.repository` | Spring Data JPA, plus the queries that need care: the atomic seat `UPDATE`, `@EntityGraph` against N+1 and `Specification` for search. |
| Domain entities separate from DTOs | Entities in `<context>.domain`; input DTOs as `record`s in `web` (`CatalogRequests`, `EnrollRequest`...); output DTOs as `record` views in `application` (`CourseView`, `EnrollmentView`...) | No endpoint receives or returns a JPA entity. Views are built by MapStruct-generated mappers (see [below](#entity--view-mapping-mapstruct)). |
| Messaging adapters isolated from the domain | Listeners in `<context>.messaging`; shared infrastructure in `messaging` (`outbox`, `inbox`, `config`, `events`) | Each listener only reads the message and delegates to `application`. Events are their own `record`s, independent of the entities. |
| Centralised error handling | `shared.web.GlobalExceptionHandler`, backed by `shared.security.ProblemDetailsSecurityHandler` and the `shared.domain` hierarchy | A single `@RestControllerAdvice` turns any controller exception into `problem+json`. The 401/403s cut short by the filter chain, before reaching a controller, use the same format. The exception type decides the status (`ConflictException` → 409, `BusinessRuleViolationException` → 422). |

Business logic never piles up in controllers or RabbitMQ listeners.

## Why layered and not hexagonal

Both a well-separated layered architecture and a hexagonal one would fit this domain. I chose the former,
borrowing hexagonal traits only where they pay off.

**What separates it from hexagonal:**

- `domain` entities carry JPA annotations. In hexagonal, the domain would be plain Java and persistence would
  adapt to it.
- Use cases call Spring Data repositories directly, instead of their own interfaces (output ports)
  implemented by a JPA adapter.
- Controllers call concrete services, without use-case interfaces (input ports).

**Why that's deliberate:** at this size, a JPA-free domain would mean duplicating every entity (domain model
+ persistence entity + mapping between them), and every repository would get an interface with a single
implementation. That's cost without real benefit. The invariants are just as protected: they live in the
entities' methods and are reinforced in the database.

**Where there are ports and adapters, because the swap is real:**

- **`PaymentGateway`** is an output port: an interface in `payment.application` with a swappable adapter
  (`SimulatedPaymentGateway`). Moving to a real gateway means adding another adapter, without touching
  `PaymentProcessor`.
- **Input adapters** (controllers in `web`, listeners in `messaging`) only translate HTTP or AMQP and
  delegate. That's how the MCP server was added: each context's `mcp` package is one more input adapter over
  the same use cases, without changing any of them.
- **The event contract** (`messaging.events`) is independent of the entities, so the published format
  doesn't change when the internal model is refactored.

If the project grew, moving to hexagonal would be incremental: extract repository interfaces into
`application` one context at a time, without redoing the rest.

Entities are rich models: valid state changes live as methods on the entity itself (`Course.publish()`,
`Enrollment.cancel()`, `Payment.confirm()`, ...) and throw a specific domain exception on an invalid
transition, instead of exposing setters and leaving validation to the caller.

## Entity → view mapping (MapStruct)

Each context has a mapper (`CatalogViewMapper`, `EnrollmentViewMapper`, `PaymentViewMapper` and
`CertificateViewMapper`) that turns entities into the `record` views the use cases return. MapStruct generates
the implementation at compile time: plain Java, no runtime reflection, readable in
`target/generated-sources/annotations`.

- **One direction only: entity → view.** Entities are created and changed through their domain methods
  (`Course.draft(...)`, `course.updateDetails(...)`), which guard their invariants. A DTO → entity mapper would
  bypass them by filling fields directly, so there isn't one.
- **A field with no source doesn't compile.** `MappingConfig` sets `unmappedTargetPolicy = ERROR`: if a field
  is added to a view and the mapper doesn't know where it comes from, the build fails instead of a `null`
  showing up in the API.
- **Tests cover what the compiler can't see:** that `categoryId` comes from the category and not the
  instructor, or that `availableSeats` is `capacity - seatsTaken`. These are `CatalogViewMapperTest` and
  `EnrollmentViewMapperTest`, without Spring.
- `availableSeats` is declared with an explicit expression. Without it, MapStruct would read
  `Course.hasAvailableSeats()` as a presence check for the field (the `hasX` convention). The result would be
  the same, but by accident.

## Invariants enforced by the database

The schema lives in `src/main/resources/db/migration` (Flyway, `ddl-auto: validate`) and repeats the critical
invariants in the database itself: `courses` has `CHECK (seats_taken <= capacity)`, and `enrollments` has a
partial unique index that stops a student from holding two `PENDING_PAYMENT`/`ACTIVE` enrollments for the same
course at once.
