#!/usr/bin/env bash
# Runs once after the devcontainer is created.
set -euo pipefail

# Named volumes are created root-owned; hand them to the dev user.
sudo chown -R vscode:vscode /home/vscode/.m2 /home/vscode/.claude 2>/dev/null || true

echo "== Toolchain"
java -version 2>&1 | head -1
mvn -v 2>/dev/null | head -1 || true
node -v
uv --version
docker version --format 'Docker (in-container daemon) {{.Server.Version}}' 2>/dev/null || echo "Docker daemon still starting"

echo "== Infra"
pg_isready -h postgres -p 5432 && echo "PostgreSQL ready"
nc -z rabbitmq 5672 && echo "RabbitMQ AMQP ready"
nc -z redis 6379 && echo "Redis ready"

echo "== Context7 docs cache"
# One-time download into .context7-docs/ (kept across rebuilds, already-cached files are skipped).
python3 .devcontainer/context7/fetch-docs.py || echo "Context7 prefetch failed; re-run .devcontainer/context7/fetch-docs.py later"

# Pre-pull the Testcontainers images into the in-container daemon (best effort, in background).
( sleep 5
  for img in postgres:18.6-alpine rabbitmq:4.3.6-management-alpine; do
    docker pull -q "$img" >/dev/null 2>&1 || true
  done ) &
