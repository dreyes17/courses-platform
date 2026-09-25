# Development environment

This folder defines a VS Code devcontainer for the project. It gives you Java 21, Maven, Docker, and running
PostgreSQL, RabbitMQ and Redis instances. You don't need to install any of them on your machine.

## Quick start

1. Install Docker Desktop and the VS Code **Dev Containers** extension.
2. Optional: create your local variables file (see [Environment variables](#environment-variables)):
   ```bash
   cp .devcontainer/.env.example .devcontainer/.env
   ```
3. In VS Code, run **Dev Containers: Reopen in Container**. The first build takes a few minutes.
4. In the container terminal, run `claude`, accept the workspace trust prompt, then run `/mcp` to check the MCP servers.

Everything works without a `.env` file. Every variable has a development default, and the two API keys are optional.

## What's inside

```
┌─────────────────────────── courses-dev (docker compose) ───────────────────────────┐
│  dev            workspace container (you work here)                                │
│                 Java 21 · Maven 3.9 · Node LTS · uv · gh · psql · jq · httpie      │
│                 Claude Code · private Docker daemon (Docker-in-Docker)             │
│  postgres       PostgreSQL 18.6        postgres:5432                               │
│  rabbitmq       RabbitMQ 4.3.6 + UI    rabbitmq:5672  (UI rabbitmq:15672)          │
│  redis          Redis 8.10             redis:6379                                  │
└────────────────────────────────────────────────────────────────────────────────────┘
```

| File | Purpose |
|---|---|
| `devcontainer.json` | Entry point for VS Code: features (Java/Maven, Docker-in-Docker, Node, GitHub CLI, Claude Code), extensions, forwarded ports |
| `docker-compose.yml` | The four services above and the environment passed to the `dev` container |
| `Dockerfile` | Workspace image: extra CLI tools, plus `uv` and a cached copy of the Python MCP servers |
| `post-create.sh` | Runs once after creation: prints tool versions, checks the services, pre-pulls the Testcontainers images |
| `.env.example` | Template for `.env` (see below) |
| `context7/` | Context7 docs pre-fetch: `topics.txt` (what to download) and `fetch-docs.py` |

**Docker-in-Docker.** The `dev` container runs its own Docker daemon. Testcontainers and the application's own
`docker compose up` run there, isolated from the containers on your machine.

**Ports forwarded to your machine:**

| Port | What |
|---|---|
| `8080` | Spring Boot app (Swagger UI at `/swagger-ui.html`) |
| `15672` | RabbitMQ management UI (`courses` / `courses`) |
| `5432` | PostgreSQL, for a desktop client |
| `6274` | MCP Inspector (see below) |

**Persistent volumes:** Maven cache (`~/.m2`), Claude Code config (`~/.claude`), Postgres data, RabbitMQ data
and the internal Docker images. Rebuilding the container keeps them.

## Environment variables

### `.devcontainer/.env`

Docker Compose reads this file when it creates the containers. It is **git-ignored**: put secrets here, never in
committed files.

| Variable | Required | Default | Used for |
|---|---|---|---|
| `POSTGRES_HOST` | no | `postgres` | Host the app and the `postgres` MCP connect to |
| `POSTGRES_PORT` | no | `5432` | Port for the above |
| `POSTGRES_DB` | no | `courses` | Database name created in PostgreSQL |
| `POSTGRES_USER` | no | `courses` | Database user |
| `POSTGRES_PASSWORD` | no | `courses` | Database password |
| `RABBITMQ_HOST` | no | `rabbitmq` | Broker host the app connects to |
| `RABBITMQ_PORT` | no | `5672` | AMQP port |
| `RABBITMQ_MANAGEMENT_PORT` | no | `15672` | Management API port (`RABBITMQ_MANAGEMENT_URL`) |
| `RABBITMQ_USER` | no | `courses` | RabbitMQ user (AMQP and management UI) |
| `RABBITMQ_PASSWORD` | no | `courses` | RabbitMQ password |
| `REDIS_HOST` | no | `redis` | Redis host the app connects to |
| `REDIS_PORT` | no | `6379` | Redis port |
| `JWT_SECRET` | no | dev-only string | Key the app uses to sign and validate JWTs. For HS256 it must be at least 32 bytes |
| `GITHUB_PERSONAL_ACCESS_TOKEN` | only for the `github` MCP | empty | Authenticates the GitHub MCP server (see below) |
| `CONTEXT7_API_KEY` | no | empty | Raises the Context7 rate limits for the MCP and the docs pre-fetch (see below) |

The `*_HOST` / `*_PORT` variables only change where the app connects, for example to point it at an external
server. They don't move the bundled services, which always listen on their standard ports.

Values can also come from your host shell (`export VAR=...` before opening VS Code). The shell takes precedence
over `.env`.

After changing `.env`, run **Dev Containers: Rebuild Container**. Compose sets the variables only when it creates
a container.

> **Credentials are set only once.** PostgreSQL and RabbitMQ create their user from these variables only the
> first time they start with an empty data volume. To change `POSTGRES_*` or `RABBITMQ_*` afterwards, delete the
> matching volume (`courses-dev_postgres-data` or `courses-dev_rabbitmq-data`) and rebuild. If you change the
> Postgres credentials, also update the SQLTools connection in `devcontainer.json`.

### Variables available inside the `dev` container

Compose builds these from the values above. You don't set them yourself.

| Variable | Value | Consumer |
|---|---|---|
| `SPRING_DATASOURCE_URL` / `_USERNAME` / `_PASSWORD` | `jdbc:postgresql://postgres:5432/courses`, … | Spring Boot (relaxed binding) |
| `SPRING_RABBITMQ_HOST` / `_PORT` / `_USERNAME` / `_PASSWORD` | `rabbitmq`, `5672`, … | Spring AMQP |
| `SPRING_DATA_REDIS_HOST` / `_PORT` | `redis`, `6379` | Spring Data Redis |
| `JWT_SECRET` | from `.env` | Your security config (`${JWT_SECRET}` in `application.yml`) |
| `DATABASE_URI` | `postgresql://courses:courses@postgres:5432/courses` | `postgres` MCP server |
| `RABBITMQ_MANAGEMENT_URL` | `http://rabbitmq:15672` | Reference for tools and scripts |
| `GITHUB_PERSONAL_ACCESS_TOKEN`, `CONTEXT7_API_KEY` | from `.env` | MCP servers |

Because of these variables, `./mvnw spring-boot:run` connects to the services with no extra config. Keep
`application.yml` portable by reading the same variables with local defaults, for example
`url: ${SPRING_DATASOURCE_URL:jdbc:postgresql://localhost:5432/courses}`.

## MCP servers

MCP servers give Claude Code tools beyond reading and editing files. They are declared in `/.mcp.json`, which is
committed and contains **no secrets**, only `${VAR}` references filled in from the environment when Claude Code
starts. `.claude/settings.local.json` (git-ignored) pre-approves them.

| Server | Runs via | What it's for in this project | Needs |
|---|---|---|---|
| `context7` | `npx @upstash/context7-mcp` | Up-to-date docs for Spring Boot 4, Spring AMQP, Spring Security, Spring AI MCP, Testcontainers and Flyway, which are newer than most models' training data | Nothing. `CONTEXT7_API_KEY` is optional |
| `postgres` | `uvx postgres-mcp` (Postgres MCP Pro) | Inspect the schema Flyway created, run SQL, `EXPLAIN` queries to find N+1 queries and missing indexes, check DB health | `DATABASE_URI` (already set) |
| `rabbitmq` | `uvx amq-mcp-server-rabbitmq` | List exchanges, queues and bindings, see what is in the DLQ, publish test or poison messages, check consumers | Connect at the start of a session (see below) |
| `github` | Remote HTTP (`api.githubcopilot.com/mcp`) | Create the repo, open PRs, check GitHub Actions runs (CI bonus) | `GITHUB_PERSONAL_ACCESS_TOKEN` |

### Context7 docs cache and rate limits

Without an API key, Context7 allows about **200 requests per month per IP**. Each `query-docs` call uses one
request, and so does each `resolve-library-id` call. To make that quota last:

- **Pre-fetched docs.** On first creation, `post-create.sh` runs `.devcontainer/context7/fetch-docs.py`. It
  downloads about 33 targeted answers, one per line of `.devcontainer/context7/topics.txt`, into `.context7-docs/`
  (about 200 KB, git-ignored). They cover every topic in the brief: the Spring Boot 4 migration, AMQP topology, DLQs
  and retries, JWT security, N+1 queries and locking, Testcontainers, the Spring AI MCP server, springdoc and
  Micrometer. The one-time download costs about 33 requests.
- **Cached, not re-downloaded.** The files live in the workspace, not the image, so rebuilding the container
  costs nothing. Files that already exist are skipped. Use `--force` to refresh them.
- **Claude reads the cache first.** `CLAUDE.md` tells Claude to check `.context7-docs/INDEX.md` before calling
  the MCP, and to add useful new queries to `topics.txt`.

To cache another topic, add a `library id | file | query` line to `topics.txt` and run
`python3 .devcontainer/context7/fetch-docs.py`. The script prints how many requests you have left.

If you still run short, a free key from <https://context7.com/dashboard> raises the limit. Put it in `.env` as
`CONTEXT7_API_KEY`. Both the MCP and the pre-fetch script use it.

### `GITHUB_PERSONAL_ACCESS_TOKEN`

Create a **fine-grained** token at <https://github.com/settings/personal-access-tokens>, restricted to the test
repository, with these permissions:

- Contents: read/write
- Pull requests: read/write
- Issues: read/write
- Actions: read
- Metadata: read (always included)

Put it in `.env` and rebuild. Without it, the `github` server shows as failed in `/mcp`; the other servers are not
affected. If a token is ever committed, revoke it on GitHub: deleting it in a later commit leaves it in the history.

### Connecting the RabbitMQ MCP

The server has no fixed broker. Ask Claude to connect at the start of a session, for example:

> Connect to RabbitMQ at host `rabbitmq`, user `courses`, password `courses`, no TLS, management port 15672.

It starts with `--allow-mutative-tools`, so it can also create queues and publish messages.

### Testing the app's own MCP server (bonus, section 16 of the brief)

Once the app includes `spring-ai-starter-mcp-server-webmvc`:

```bash
npx @modelcontextprotocol/inspector      # open http://localhost:6274 and connect to http://localhost:8080/mcp (or /sse)
claude mcp add --transport http courses http://localhost:8080/mcp   # use its tools from Claude Code
```

## Bootstrapping the project

```bash
curl -s https://start.spring.io/starter.tgz \
  -d type=maven-project -d javaVersion=21 -d bootVersion=4.1.1.RELEASE \
  -d groupId=com.example -d artifactId=courses -d name=courses \
  -d dependencies=web,data-jpa,postgresql,flyway,rabbitmq,validation,security,oauth2-resource-server,actuator,prometheus,testcontainers \
  | tar -xzf -
sed -i 's|4.1.1.RELEASE|4.1.1|' pom.xml   # Initializr writes its metadata id into the parent version
./mvnw test
```

In the generated Testcontainers config, replace `postgres:latest` and `rabbitmq:latest` with
`postgres:18.6-alpine` and `rabbitmq:4.3.6-management-alpine`, so tests use the same versions as the dev services
and the images `post-create.sh` already pulled.

## Troubleshooting

| Symptom | Fix |
|---|---|
| MCP servers show "pending approval" | Run `claude` interactively once in `/workspaces/techlead` and accept the trust prompt |
| Postgres fails to start after a version change | The data volume was created by another major version: `docker volume rm courses-dev_postgres-data`, then rebuild |
| `.env` change has no effect | Rebuild the container; restarting it isn't enough |
| Testcontainers: "Could not find a valid Docker environment" | The internal Docker daemon is still starting. Wait a few seconds or run `docker info` |
