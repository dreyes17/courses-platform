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

## Environment

See `.devcontainer/README.md`. Services: `postgres:5432`, `rabbitmq:5672` (UI on 15672), `redis:6379`; credentials `courses`/`courses`.
