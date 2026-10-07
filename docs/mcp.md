# MCP integration

The application is also an MCP (Model Context Protocol) server. An AI assistant can browse the catalog, enroll
students or follow an enrollment using the application's real logic, with the same permissions as its token.

**Dependency:** `org.springframework.ai:spring-ai-starter-mcp-server-webmvc`, Spring AI's official MCP server
starter, version 2.0.1 (compatible with Spring Boot 4). Tools are methods annotated with `@McpTool` and
`@McpToolParam` in `catalog.mcp.CatalogTools`, `catalog.mcp.InstructorTools`, `enrollment.mcp.EnrollmentTools`,
`payment.mcp.PaymentTools` and `certificate.mcp.CertificateTools`.

## Design

- **It's just one more input adapter, like the controllers.** Each tool validates its input with Jakarta
  Validation, applies the same `@PreAuthorize` rule as its REST endpoint and delegates to the same use case.
  There's no MCP-specific logic or data.
- **Same token, same permissions.** `/mcp` is protected by the API security chain: without
  `Authorization: Bearer` it answers 401, and each tool runs with that token's permissions. For example, a STUDENT
  can't use `create_category` and only sees `PUBLISHED` courses, and an INSTRUCTOR doesn't see other instructors'
  drafts.
- **Stateless (`protocol: STATELESS`).** Each call is an independent HTTP request, like the REST API: any instance
  can serve it and there's no MCP session to keep. It also means the tool runs on the request's thread, with its
  security context.
- **Errors without internal details.** `McpToolErrors` is the counterpart of `GlobalExceptionHandler`: the client
  only receives messages written by the application (`Course … not found`,
  `Invalid arguments: progress must be less than or equal to 100`). Any unexpected error arrives as
  `An unexpected error occurred` and is logged.
- **Its own rate limit.** `/mcp` has its own per-user limit, which also covers `enroll_student` (see
  [Rate limiting](security.md#rate-limiting)).

## Available tools

Parameters marked `?` are optional. In lists, `page` starts at 0 and `size` ranges from 1 to 100 (default 20).

| Tool | What it does | Parameters | Who can use it |
|---|---|---|---|
| `list_categories` | Lists categories alphabetically | `name?`, `status?`, `page?`, `size?` | any user |
| `get_category` | Returns a category | `categoryId` | any user |
| `create_category` | Creates a category (unique name) | `name`, `description?` | ADMIN |
| `update_category` | Renames a category or changes its description | `categoryId`, `name`, `description?` | ADMIN |
| `archive_category` | Archives a category: no new courses; existing ones are kept | `categoryId` | ADMIN |
| `activate_category` | Reactivates an archived category | `categoryId` | ADMIN |
| `delete_category` | Deletes a category with no courses (otherwise archive it) | `categoryId` | ADMIN |
| `list_courses` | Lists courses, newest first | `page?`, `size?` | any user (ADMIN sees all; an INSTRUCTOR, `PUBLISHED` plus their own; a STUDENT, only `PUBLISHED`) |
| `search_courses` | Searches courses with combinable filters | `categoryId?`, `level?`, `minPrice?`, `maxPrice?`, `title?`, `withAvailableSeats?`, `status?`, `page?`, `size?` | any user (same visibility as above) |
| `get_course` | Returns a course with its available seats | `courseId` | any user (same visibility as above) |
| `create_course` | Creates a course as `DRAFT` | `title`, `description?`, `durationHours`, `level`, `price`, `capacity`, `categoryId`, `instructorId` | ADMIN, or the INSTRUCTOR who teaches it |
| `publish_course` | Publishes a `DRAFT` course | `courseId` | ADMIN or the course's instructor |
| `update_course` | Changes a course's details (capacity can't drop below seats taken) | `courseId`, `title`, `description?`, `durationHours`, `level`, `price`, `capacity` | ADMIN or the course's instructor |
| `archive_course` | Archives a course: it stops accepting enrollments | `courseId` | ADMIN or the course's instructor |
| `delete_course` | Deletes a `DRAFT` course (a published one can only be archived) | `courseId` | ADMIN or the course's instructor |
| `create_instructor` | Creates an instructor and their login account (unique email) | `name`, `email`, `bio?`, `password` | ADMIN |
| `list_instructors` | Lists instructors alphabetically | `name?`, `email?`, `page?`, `size?` | ADMIN |
| `get_instructor` | Returns an instructor | `instructorId` | ADMIN or the instructor themself |
| `update_instructor` | Changes name and bio (the email doesn't change) | `instructorId`, `name`, `bio?` | ADMIN or the instructor themself |
| `delete_instructor` | Deletes an instructor with no courses, together with their account | `instructorId` | ADMIN |
| `list_students` | Lists students | `name?`, `email?`, `page?`, `size?` | ADMIN |
| `get_student` | Returns a student | `studentId` | ADMIN or the student themself |
| `enroll_student` | Enrolls the token's student: reserves a seat and creates the payment, returning `PENDING_PAYMENT` | `courseId`, `idempotencyKey` | STUDENT |
| `list_enrollments` | Lists all enrollments, newest first, with student and course | `courseId?`, `studentId?`, `status?`, `page?`, `size?` | ADMIN |
| `get_enrollment` | Returns an enrollment's status and progress | `enrollmentId` | ADMIN, its student or the course's instructor |
| `get_enrollment_payment` | Returns an enrollment's payment, with `failureReason` if it failed | `enrollmentId` | ADMIN or its student |
| `get_enrollment_certificate` | Returns a completed enrollment's certificate, with its code | `enrollmentId` | ADMIN, its student or the course's instructor |
| `verify_certificate` | Checks that a certificate code is genuine: holder, course and date | `code` | any user |
| `update_enrollment_progress` | Sets progress (0–100). At 100 the enrollment completes and the certificate is issued | `enrollmentId`, `progress` | ADMIN or its student |
| `cancel_enrollment` | Cancels an enrollment and releases its seat | `enrollmentId` | ADMIN or its student |
| `list_students_by_course` | Lists the students enrolled in a course | `courseId`, `status?`, `page?`, `size?` | ADMIN or the course's instructor |
| `list_courses_by_student` | Lists a student's courses | `studentId`, `status?`, `page?`, `size?` | ADMIN or the student themself |

- `idempotencyKey` is required in `enroll_student` for the same reason as the `Idempotency-Key` header in REST: an
  agent that retries after a *timeout* gets the original enrollment instead of taking another seat.
- Delete tools return `{"deletedId": ...}`, where the REST API answers 204 with no body.
- Each tool declares its MCP *hints* (`readOnlyHint` on queries, `idempotentHint`, `destructiveHint`). Clients use
  them to decide when to ask for confirmation.

## Trying it out

With the app running (Compose or `./mvnw spring-boot:run`), get an ADMIN token. Outside the devcontainer, load
your `.env` credentials first with `set -a; . ./.env; set +a`; inside, the shell already has them (see the note
on devcontainer variables in [Getting started](getting-started.md#docker-compose)):

```bash
TOKEN=$(curl -s localhost:8080/api/auth/token -H 'Content-Type: application/json' \
  -d "{\"email\":\"$ADMIN_EMAIL\",\"password\":\"$ADMIN_PASSWORD\"}" | jq -r .accessToken)
```

- **MCP Inspector:** `npx @modelcontextprotocol/inspector` and open <http://localhost:6274>. Pick the
  *Streamable HTTP* transport with URL `http://localhost:8080/mcp`, and add the header
  `Authorization: Bearer <token>` in the authentication settings. *List Tools* shows all 32 tools with their
  parameters, and each one can be run from a form. In the devcontainer, port 6274 is already forwarded.
- **MCPJam:** `npx @mcpjam/inspector@latest`, with the same URL and header.
- **curl:** since it's stateless, each call is one JSON-RPC request:

  ```bash
  curl -s localhost:8080/mcp -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
    -H 'Accept: application/json, text/event-stream' \
    -d '{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"search_courses","arguments":{"title":"spring"}}}'
  ```

- **Claude Code:** `claude mcp add --transport http courses http://localhost:8080/mcp --header "Authorization: Bearer $TOKEN"`.

`McpServerTest` walks through all of this with the official MCP SDK client, over real HTTP:

- the tool list;
- the full flow: create a category and a course, publish, search, enroll, retry with the same key, watch the
  asynchronous activation over RabbitMQ and the confirmed payment, complete the course, get the certificate and
  verify its code;
- permissions;
- validation and error messages;
- the 401 without a token.

Also verified on the Compose stack with `curl`: 32 tools, `search_courses` returns the real courses,
`create_category` as a STUDENT answers `You are not allowed to perform this operation` and without a token the
response is 401. An instructor gets `not found` when asking for another instructor's draft, and
`delete_instructor` answers 409 while the instructor has courses; without courses it deletes them and their
account can no longer log in.
