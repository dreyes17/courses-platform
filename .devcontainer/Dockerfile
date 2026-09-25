# Java 21 (Temurin) + Maven-ready Debian image maintained by the devcontainers project.
ARG JAVA_VERSION=21
FROM mcr.microsoft.com/devcontainers/java:1-${JAVA_VERSION}-bookworm

# CLI tools handy for this project: psql for poking at the DB, jq/httpie for the REST API.
# The base image's Yarn apt repo has an expired key and breaks apt-get update; Yarn isn't needed.
RUN rm -f /etc/apt/sources.list.d/yarn.list \
    && apt-get update \
    && export DEBIAN_FRONTEND=noninteractive \
    && apt-get install -y --no-install-recommends postgresql-client jq httpie netcat-openbsd \
    && apt-get clean && rm -rf /var/lib/apt/lists/*

# uv / uvx: runs the Python-based MCP servers (Postgres MCP Pro, RabbitMQ MCP).
COPY --from=ghcr.io/astral-sh/uv:0.8 /uv /uvx /usr/local/bin/

# Pre-warm the uv cache so the MCP servers start instantly the first time.
# Keep these versions in sync with /.mcp.json.
USER vscode
RUN uvx --from postgres-mcp==0.3.0 --with "mcp<2" postgres-mcp --help >/dev/null \
    && uvx --from amq-mcp-server-rabbitmq==4.0.0 amq-mcp-server-rabbitmq --help >/dev/null \
    || true
USER root
