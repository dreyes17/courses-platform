# REST API

- [Resources](#resources)
- [Conventions](#conventions)
- [Cursor pagination](#cursor-pagination)
- [Errors](#errors)

All endpoints live under `/api` and are documented in Swagger UI (<http://localhost:8080/swagger-ui.html>) and
in the OpenAPI spec (`/v3/api-docs`). For authentication and roles, see [Security](security.md).

## Resources

| Resource | Operations | List filters |
|---|---|---|
| `/api/categories` | create, list, get, rename (`PUT`), `POST /{id}/archive`, `POST /{id}/activate`, delete (409 if it has courses) | `name`, `status` |
| `/api/auth` | `POST /register` (public student sign-up), `POST /token` (login → JWT) | — |
| `/api/instructors` | create an instructor and their account (unique email), list, get, update profile, delete together with the account (409 if they have courses) | `name`, `email` |
| `/api/courses` | create (as `DRAFT`), search, search by cursor (`GET /scroll`), get, update, `POST /{id}/publish`, `POST /{id}/archive`, delete (`DRAFT` only) | see *Course search* below |
| `/api/students` | list, get | `name` (first or last name), `email` |
| `/api/enrollments` | enroll (`Idempotency-Key` required; the student comes from the token), list all (ADMIN only), get, `PUT /{id}/progress`, `POST /{id}/cancel` | `courseId`, `studentId`, `status` |
| `/api/enrollments/{id}/payment` | the enrollment's payment: amount, status and, if it failed, `failureReason` (why an enrollment was `CANCELLED` by the charge) | — |
| `/api/enrollments/{id}/certificate` | certificate of a `COMPLETED` enrollment, with its verifiable code (404 until issued) | — |
| `/api/certificates/{code}` | **public** verification, no token: holder, course and issue date | — |
| `/api/courses/{id}/enrollments` | students enrolled in a course | `status` |
| `/api/students/{id}/enrollments` | a student's courses | `status` |

**The spec is enough to integrate.** Each operation documents its real success code (`201` on creation, `204`
on deletion) and all its errors, with an `application/problem+json` body (`ProblemDetail` schema). Errors that
follow from the operation's shape are added to all of them by `OpenApiConfig`:

| Error | Documented when |
|---|---|
| 400 | the operation takes a body or parameters |
| 401 | it needs a token (all except login, sign-up and certificate verification) |
| 403 | it has a role or ownership rule (`@PreAuthorize`) |
| 404 | it addresses a resource by id in the path |

Those that depend on business rules (409, 422, 429, and the 404 for an id sent in the body) are declared on
each endpoint with `@ApiResponse` and a description of the specific case: for example, `POST /api/enrollments`
documents 409 for *course full, not published or student already enrolled* and 422 for *`Idempotency-Key`
reused with a different request*. `ApiDocumentationTest` checks that every operation has exactly one success
response with its body, and that every error uses the `ProblemDetail` schema.

## Conventions

- **State transitions as actions.** Publish, archive and cancel are `POST`s on a sub-resource, not a `PUT` that
  changes the `status` field. That way each transition's business rule lives in a single domain method.
- **Pagination, sorting and filtering on every list.** Lists accept `page`, `size` (default 20, max 100) and
  `sort` (entity properties, e.g. `sort=price,desc`), plus the filters in the table above. Filters are
  optional and combinable; text filters do a case-insensitive substring match. Responses are
  `{content, page, size, totalElements, totalPages}`. No endpoint returns a whole table. Course search also
  offers [cursor pagination](#cursor-pagination).
- **Rate limiting** on login, sign-up, enrollment and MCP: 429 with `Retry-After` (see
  [Rate limiting](security.md#rate-limiting)).
- **Course search.** All filters are optional and combinable: `categoryId`, `level`, `minPrice`, `maxPrice`,
  `title` (case-insensitive substring) and `withAvailableSeats=true`, plus `status`.
- **Course visibility.** ADMIN sees everything; an INSTRUCTOR sees `PUBLISHED` courses plus their own in any
  state; a STUDENT sees only `PUBLISHED` ones. The rule (`CourseVisibility`) applies equally to search, the
  cursor, `GET /api/courses/{id}` and the MCP tools. Someone else's draft returns 404, not 403, so as not to
  reveal that it exists, and the `status` filter combines with the rule: a STUDENT asking for `status=DRAFT`
  gets an empty list.
- **Correlation.** Any request may send `X-Correlation-Id` (one is generated otherwise), and the response always
  echoes it. It finds everything that request caused in the logs (see
  [Observability](observability.md#correlated-logs)).
- **Verifiable certificates.** The code (`CERT-` + 16 random hex characters, 64 bits) is what the student shows,
  for example to an employer, and `GET /api/certificates/{code}` confirms it without an account. It returns only
  what the certificate itself shows (holder, course, date), with no ids or email. The code can't be brute-forced.
- **No N+1 on relational lists.** Courses with their category and instructor, a course's students, a student's
  courses and the global enrollment list (student and course) are loaded with `@EntityGraph` over *to-one*
  relationships, so pagination still happens in SQL. `QueryEfficiencyTest` counts the thread's SQL statements:
  each page costs at most 2 queries, whatever its size.

## Cursor pagination

`GET /api/courses/scroll` takes the same filters as search and sorts newest first, paginating by *keyset*. Each
response's `nextCursor` marks the last course returned. Send it back as `cursor` to get the next page; it is
`null` on the last one:

```bash
curl "localhost:8080/api/courses/scroll?size=20&level=BEGINNER" -H "Authorization: Bearer $TOKEN"
# {"content": [ ... ], "nextCursor": "MjAyNi0wOS0yOVQxMjoxMToyOS4xNDVafDFm..."}
curl "localhost:8080/api/courses/scroll?size=20&level=BEGINNER&cursor=MjAyNi0wOS0y..." -H "Authorization: Bearer $TOKEN"
```

How it differs from page-number pagination, which `GET /api/courses` keeps:

- **No duplicates or gaps.** With `page=1`, the database skips the first N rows at query time. If a course is
  created between two pages, everything shifts by one row and the client sees a course twice. The cursor
  points at a specific position, `(createdAt, id)`, so the next page starts right after the last course seen.
  `CursorPaginationTest` reproduces it: it creates a course mid-scroll and checks that nothing is repeated or
  skipped with the cursor, while `page=1` repeats a course.
- **Constant cost.** The condition `(created_at, id) < cursor` walks the index
  `(status, created_at DESC, id DESC)` (`V4__courses_keyset_index.sql`), so page one thousand costs the same as
  the first. With `OFFSET`, the database has to scan and discard every earlier row.
- **No count query.** It reads `size + 1` rows: if the extra one arrives, there's a next page. Each page is a
  single query, with category and instructor in the same `JOIN` (`QueryEfficiencyTest`).
- **Total order.** `id` breaks ties between courses created at the same instant, so no course can be dropped by
  a tie.
- **Opaque cursor.** It's Base64 of `createdAt|id`. A tampered or invented one returns 400 (`Invalid cursor`).

When to use which: the cursor for walking long or endless lists (an app's infinite scroll, an export); page
numbers when you need to jump to a specific page, sort by other fields or know the total.

## Errors

`GlobalExceptionHandler` always answers `application/problem+json` (RFC 9457) with `title`, `detail`, `status`
and `instance`. Only messages written by the application reach the client: any unexpected exception is logged
on the server and returned as a generic 500.

| Situation | Exception | HTTP |
|---|---|---|
| Invalid body (Bean Validation) | `MethodArgumentNotValidException` → includes per-field `errors` | 400 |
| Missing required header, `sort` on a non-existent property, filter or id with an invalid value | `MissingRequestHeaderException`, `PropertyReferenceException`, `MethodArgumentTypeMismatchException` | 400 |
| Resource not found | `ResourceNotFoundException` | 404 |
| Course full, double enrollment, invalid state transition, duplicate, resource in use | subclasses of `ConflictException` | 409 |
| Concurrent modification, database constraint violation | `OptimisticLockingFailureException`, `DataIntegrityViolationException` | 409 |
| Business rule (progress going backwards, capacity below seats taken, archived category) | `BusinessRuleViolationException` | 422 |
| `Idempotency-Key` reused with a different request | `IdempotencyKeyReusedException` | 422 |

The domain doesn't throw `IllegalArgumentException` for business rules: it has its own exception hierarchy.
That lets the handler translate each case precisely without catching generic framework exceptions, whose
messages could leak internal details.
