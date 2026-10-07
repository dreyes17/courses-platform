# Security

- [Authentication](#authentication)
- [Authorization](#authorization)
- [Passwords and secrets](#passwords-and-secrets)
- [Rate limiting](#rate-limiting)
- [Actuator and the management port](#actuator-and-the-management-port)

## Authentication

**JWT bearer** tokens issued by the application itself, with no external identity provider:

1. A student signs up at `POST /api/auth/register`. Instructors are created by an ADMIN at
   `POST /api/instructors`, password included. The first ADMIN is created at startup from
   `ADMIN_EMAIL`/`ADMIN_PASSWORD`.
2. `POST /api/auth/token` with email and password returns an HS256 token signed with `JWT_SECRET`, valid for
   1 hour. It carries `sub` (user id), `roles` and, depending on the role, `studentId` or `instructorId`.
3. Every request sends `Authorization: Bearer <token>`. The app validates it as a *resource server*:
   signature, expiry and issuer `courses-api`.

```bash
set -a; . ./.env; set +a    # outside the devcontainer; inside, the variables are already set
TOKEN=$(curl -s localhost:8080/api/auth/token -H 'Content-Type: application/json' \
  -d "{\"email\":\"$ADMIN_EMAIL\",\"password\":\"$ADMIN_PASSWORD\"}" | jq -r .accessToken)
curl localhost:8080/api/students -H "Authorization: Bearer $TOKEN"
```

## Authorization

The API filter chain only separates public endpoints (login, sign-up, certificate verification, Swagger) from
authenticated ones. Role and ownership rules sit next to each endpoint with `@PreAuthorize`, backed by
`AccessRules` (`@access.ownsEnrollment(...)`, `@access.teachesCourse(...)`), which runs lightweight `exists`
queries:

| Role | Can |
|---|---|
| ADMIN | Everything: manage the catalog and instructors, see any student, enrollment, payment, certificate or list, including the global enrollment list (`GET /api/enrollments`) |
| INSTRUCTOR | See the published catalog and **their own** courses in any state (not other instructors' drafts); create courses under their name; edit, publish, archive and delete **their** courses; see the enrollments and certificates of **their** courses |
| STUDENT | See the catalog (`PUBLISHED` courses only); enroll, which starts the payment (see [Enrollment flow](messaging.md#enrollment-flow)); see, update progress on and cancel **their** enrollments, and see their payment and certificate |
| Anonymous | Verify a certificate by its code |

- When enrolling, the student is taken from the token and the body only carries `courseId`. Enrolling someone
  else is impossible.
- On someone else's resource the answer is **403 even if the id doesn't exist**, so ids aren't revealed to
  those without access.
- 401 (no token, invalid or expired token, wrong credentials) and 403 are `problem+json`, like every other
  error.

## Passwords and secrets

- Passwords are stored only as BCrypt hashes (`{bcrypt}...`, via `DelegatingPasswordEncoder`, which allows
  migrating algorithms later). They're never logged.
- A login with an unknown email still compares against a dummy hash. Response time doesn't reveal which emails
  have an account, and both cases return the same message.
- `JWT_SECRET` and the ADMIN credentials only come from environment variables. `application.yml` has no
  default for the secret.
- Migration `V2__user_accounts.sql` guarantees in the database that each role is linked to exactly its profile
  (student, instructor, or none for ADMIN).

## Rate limiting

Per-client *token bucket* limits (Bucket4j), stored in **Redis** so they hold across all instances
(`RateLimitFilter`, `RedisBuckets`):

| Rule | Endpoint | Default limit | Keyed by | Prevents |
|---|---|---|---|---|
| `login` | `POST /api/auth/token` | 10 per minute | IP | password guessing |
| `registration` | `POST /api/auth/register` | 20 per hour | IP | mass account creation |
| `enrollment` | `POST /api/enrollments` | 30 per minute | user | a client retrying in a loop |
| `mcp` | `POST /mcp` | 120 per minute | user | an AI agent in a loop; also covers `enroll_student` |

- Over the limit, the response is **429** as `problem+json`, with `Retry-After` in seconds. Allowed requests
  carry `X-RateLimit-Remaining`.
- **By IP or by user.** Login and sign-up are anonymous, so they count by IP. The rest count by user, because
  the filter runs after JWT authentication: students behind the same IP (a university, a NAT) don't share a
  budget.
- **The IP is the direct client's.** `X-Forwarded-For` is never read, since anyone can forge it. Behind a
  trusted reverse proxy, `server.forward-headers-strategy` makes the IP the real client's.

**Why Redis and not memory.** With in-memory counters, each instance counts on its own: with N instances, the
effective limit is N times the configured one, and a brute-force limit that multiplies like that is weaker than
it looks. In Redis, every instance shares one budget per client, and it survives an app restart.

- **No races between instances.** Bucket4j updates each bucket with *compare-and-swap*, so two simultaneous
  requests on different instances never consume the same token.
- **Redis doesn't grow unbounded.** Each key (`rate-limit:<rule>:<ip|user>:<id>`) expires when its bucket would
  have fully refilled. An idle client takes no space, and losing its key changes nothing.
- **It stores nothing else.** That's why it has no volume in Compose: losing Redis only resets the counters.

**If Redis fails, requests pass (*fail-open*).** A deliberate choice: losing the protection shouldn't take the
API down with it, and login would still be protected by BCrypt's cost. So that it never happens silently:

- Each check has a 200 ms timeout (`app.rate-limit.redis-timeout`). Without a connection, operations are
  rejected immediately instead of queueing, and reconnection is attempted at most every 5 seconds. A Redis
  outage adds no noticeable latency.
- Each request that passes unchecked increments `courses_rate_limit_unavailable_total`. The
  `RateLimitingUnavailable` alert fires as soon as it appears, and the Grafana rate-limiting panel shows it.
- Redis appears in `/actuator/health` but not in the *readiness* probe: an instance without Redis can still
  serve traffic.
- The app starts even if Redis is down: the connection opens on first use.

**Verified on the Compose stack:**

- the 11th login in a row gets a 429 and the bucket appears in Redis;
- after restarting the app, the limit still holds;
- with Redis stopped, logins pass, are counted, the instance stays *ready* and the alert goes to `firing`;
- when Redis comes back, limiting resumes without restarting the app.

Limits are configured under `app.rate-limit.*` and can be turned off with `RATE_LIMIT_ENABLED=false`.
Rejections are counted in `courses_rate_limit_rejected_total{rule}`.

## Actuator and the management port

Actuator (`health`, `info`, `prometheus`) isn't served on the API port. It has its own management port:
`management.server.port`, 8081 by default, configurable with `MANAGEMENT_SERVER_PORT`.

| Port | Serves | Who reaches it | Authentication |
|---|---|---|---|
| 8080 | API and Swagger | clients (published in Compose) | JWT, except login, sign-up and Swagger |
| 8081 | `/actuator/health`, `/actuator/info`, `/actuator/prometheus` | only the deployment's internal network (`expose`, no `ports`) | none |

**Why.** Prometheus and orchestrator probes hit these endpoints every few seconds with a fixed configuration and
can't obtain a JWT, which expires after an hour anyway. There were three options:

- leave metrics public on 8080, exposing internal data;
- give the scraper a static credential, one more secret to manage and rotate;
- isolate Actuator at the network level.

I chose the third, the usual practice with containers. Isolation comes from the network: 8081 is never
published, so no authentication is needed. That's also why `health` shows database and RabbitMQ details.

**Probes.** `/actuator/health/readiness` includes the database and RabbitMQ (`readinessState,db,rabbit`): an
instance without a database or broker stops receiving traffic. Redis is left out on purpose, because without it
only rate limiting degrades. `/actuator/health/liveness` excludes them on purpose too. If the database went down
and liveness depended on it, the orchestrator would restart healthy processes, which fixes nothing.

**How it's applied.**
- The API security chain would also apply to the management port, so `SecurityConfig` defines its own,
  higher-priority chain for it.
- That chain only applies when the request arrives on the management server's **actual** port (recorded by
  `ManagementPort` at startup) **and** its path is under `/actuator`. It works with a random port too, and even
  if someone configured the same port for API and management, this chain could never open up the API.

**`/actuator/info`** describes what's running: name, version and build time (the `build-info` goal of
`spring-boot-maven-plugin`) and the Java version.

In Compose, the `prometheus` service scrapes `http://app:8081/actuator/prometheus` over that internal network
(see [Prometheus and alerts](observability.md#prometheus-and-alerts)). You can also check it by hand:

```bash
docker run --rm --network courses_default curlimages/curl -s http://app:8081/actuator/health
# {"status":"UP","components":{"db":{"status":"UP",...},"rabbit":{"status":"UP",...},...}}
```
