# Getting started

- [Run the whole platform with Docker Compose](#docker-compose)
- [Develop in the devcontainer](#devcontainer)
- [Without the devcontainer](#without-the-devcontainer)

## Docker Compose

Only Docker is required.

```bash
cp .env.example .env          # local secrets; .env is git-ignored
docker compose up --build     # PostgreSQL + RabbitMQ + Redis + the app + Prometheus, Jaeger and Grafana
```

| Service | URL | Notes |
|---|---|---|
| API | <http://localhost:8080> | Swagger UI at `/swagger-ui.html`, OpenAPI spec at `/v3/api-docs` |
| MCP server | `http://localhost:8080/mcp` | See [MCP integration](mcp.md) |
| RabbitMQ UI | <http://localhost:15672> | Credentials from `.env`. Useful for watching the queues and DLQs |
| Prometheus | <http://localhost:9090> | Bound to `127.0.0.1` only. See [Prometheus and alerts](observability.md#prometheus-and-alerts) |
| Grafana | <http://localhost:3000> | Bound to `127.0.0.1` only, dashboard preloaded. See [Dashboard](observability.md#dashboard-grafana) |
| Jaeger | <http://localhost:16686> | Bound to `127.0.0.1` only. See [Distributed tracing](observability.md#distributed-tracing-opentelemetry--jaeger) |

To fill the dashboards with data, run `./scripts/demo-traffic.sh` (needs `curl` and `jq`): it creates a small
catalog, enrolls students, triggers an approved and a declined payment, a full course, certificates and a burst of
rate-limited logins.

An ADMIN account is created from `ADMIN_EMAIL`/`ADMIN_PASSWORD`. See [Security](security.md) for how to get a
token (inside the devcontainer, read the note below first). `docker compose down -v` stops everything and
deletes the data volumes.

> **Running `docker compose` from inside the devcontainer:** that environment has its own Docker
> (Docker-in-Docker), so ports are published on the container's `localhost`, not your machine's. To reach the
> API from your browser, your IDE or devcontainer tool has to forward the container's port `8080` to your
> machine. `devcontainer.json` declares it in `forwardPorts`, but not every IDE applies that automatically. If
> `http://localhost:8080` doesn't answer, forward port `8080` with your IDE's mechanism and use the local
> address it assigns, which may not be 8080 if that port is already taken on your machine. Do the same with
> `15672` for this stack's RabbitMQ UI, and with `9090`, `3000` and `16686` for Prometheus, Grafana and Jaeger.
> Watch out: your machine's `15672` may already point to the devcontainer's own RabbitMQ, which is a different
> instance with different credentials.
>
> **Devcontainer variables take precedence over `.env`.** Docker Compose prefers shell variables over `.env`,
> and the devcontainer already exports `JWT_SECRET`, `ADMIN_EMAIL` and `ADMIN_PASSWORD` with development
> values (see [`.devcontainer/README.md`](../.devcontainer/README.md)). So when launched from inside the
> devcontainer, the Compose stack's ADMIN uses the devcontainer password, not the one in your `.env`. The
> examples in these docs read `$ADMIN_EMAIL`/`$ADMIN_PASSWORD` from the shell, so they work as-is inside the
> devcontainer. If you'd rather use the `.env` values, start with
> `env -u JWT_SECRET -u ADMIN_EMAIL -u ADMIN_PASSWORD docker compose up --build`.
>
> None of this applies if you run `docker compose` directly on your machine.

How the deployment behaves:

- **Secrets** come only from the environment or `.env`. If a required one is missing, `docker compose` stops
  and says which.
- **Startup order:** the app waits for Postgres and RabbitMQ to pass their healthchecks. Its own healthcheck
  calls `/actuator/health/readiness` on the management port (see
  [Actuator and the management port](security.md#actuator-and-the-management-port)).
- **Ports:** 8080 (API and MCP server) and 15672 (RabbitMQ UI) are published; 9090 (Prometheus), 3000
  (Grafana) and 16686 (Jaeger) only on `127.0.0.1`. Actuator listens on 8081, reachable only inside the Docker
  network.
- **App startup:** Flyway creates the schema and the RabbitMQ topology declares itself, with no manual steps.
- **Image:** the `Dockerfile` builds with the `mvnw` wrapper in a JDK stage and runs in a JRE stage as an
  unprivileged user. It uses Spring Boot layers, so a code change doesn't re-ship the dependencies.
- **Tests:** the image build doesn't run tests. The integration tests need Docker (Testcontainers) and run
  separately with `./mvnw test`.

## Devcontainer

The repository includes a devcontainer ([`.devcontainer/`](../.devcontainer/)): the full development
environment, versioned with the code and based on the open [Dev Containers](https://containers.dev)
specification. Opening the project with it gives you a container with everything installed, configured and
running.

**What it includes**

- **Tools:** Java 21, Maven, its own Docker (Docker-in-Docker), `psql`, `jq` and `httpie`.
- **Services:** PostgreSQL 18.6, RabbitMQ 4.3.6 (with its management UI) and Redis, already running.
- **Environment variables:** the `SPRING_*` ones the app needs to reach those services, plus development
  values for `JWT_SECRET` and `ADMIN_EMAIL`/`ADMIN_PASSWORD`.
- **Preloaded images:** the Testcontainers images, pulled on first creation.
- **MCP servers for AI assistants:** up-to-date library docs; inspection of the database, the broker and the
  rate-limit buckets in Redis; Prometheus queries and Jaeger traces; and GitHub. Details in
  [`.devcontainer/README.md`](../.devcontainer/README.md).

**Why it's worth it**

- **Reproducible:** anyone working on the project uses the same Java, Maven, PostgreSQL and RabbitMQ versions it
  was developed with, which also match `docker-compose.yml` and Testcontainers. No more "works on my machine".
- **Nothing to install** besides Docker, and no interference with what you already have. The container's own
  Docker isolates the Testcontainers and `docker compose` containers, and the JDK and tools don't mix with
  yours.
- **Ready from minute one:** services are up and variables injected, so `./mvnw spring-boot:run` starts with no
  setup. With the images preloaded, the first `./mvnw test` doesn't wait for downloads.
- **Environment as code:** it lives in the repository, is reviewed in commits and evolves with the project,
  instead of relying on setup instructions that go stale.

**How to use it**

1. Requirements: Docker and an IDE or tool that supports Dev Containers, such as VS Code and its derivatives,
   JetBrains IDEs or the official `devcontainer` CLI.
2. Optional: `cp .devcontainer/.env.example .devcontainer/.env` to change credentials or add API keys.
   Everything works without that file.
3. Open the repository "in the container" from your IDE, or with the CLI:
   `devcontainer up --workspace-folder .`. The first build takes a few minutes. When it finishes, a script
   checks the tools and that the services respond.
4. Inside the container:

   ```bash
   ./mvnw test              # unit + integration; Testcontainers starts PostgreSQL and RabbitMQ
   ./mvnw spring-boot:run    # runs against the devcontainer's services
   ```

   The API is on the container's port 8080. `devcontainer.json` asks to forward it to your machine; if your
   IDE doesn't, forward it with its port-forwarding feature.

Environment variables are set when the container is created. After changing `.devcontainer/.env`, rebuild it;
restarting isn't enough. To avoid a rebuild, pass a variable for a single run:
`ADMIN_EMAIL=... ADMIN_PASSWORD=... ./mvnw spring-boot:run`.

**`docker compose up` and `./mvnw spring-boot:run` side by side in the devcontainer.** They are two independent
ways to run the app, and it helps to know how they coexist:

- **They share a port.** The Compose app already holds the container's 8080, so `spring-boot:run` fails with
  *"Port 8080 was already in use"*. 8081 doesn't clash, because Compose doesn't publish it.
- **They don't share data.** `spring-boot:run` uses the devcontainer's PostgreSQL and RabbitMQ (the `SPRING_*`
  variables point there). The Compose stack has its own database and broker, unreachable from outside its
  network. What you create in one doesn't show up in the other.

Depending on what you need:

```bash
# Just use the API: the Compose app on 8080 is enough; no need for spring-boot:run.

# Run from source (e.g. to debug): stop the Compose app and start yours
docker compose stop app          # or `docker compose down` to stop the whole stack
./mvnw spring-boot:run

# Both at once: start yours on other ports
SERVER_PORT=8090 MANAGEMENT_SERVER_PORT=8091 ./mvnw spring-boot:run
```

## Without the devcontainer

Java 21 and Docker on your machine are enough. `./mvnw test` starts its own containers with Testcontainers,
and `docker compose up --build` runs the whole platform.

The app **refuses to start without `JWT_SECRET`** (at least 32 bytes). That's deliberate: it can never run with
a default key. If you start it with `./mvnw spring-boot:run` outside the devcontainer, export that variable
first, together with `ADMIN_EMAIL`/`ADMIN_PASSWORD` if you want an ADMIN account.
