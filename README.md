# Quarkus Enterprise Starter

A standards-based, secure starter for our Quarkus + Java 21 + PostgreSQL services.
Clone it, build your module on the `applicant` reference, and ship it behind the API gateway.

- **Stack:** Java 21 (LTS), Quarkus, PostgreSQL, Maven — see [SETUP.md](SETUP.md) for details.
- **The rules AI agents and humans must follow:** [CLAUDE.md](CLAUDE.md).

---

## 1. Local setup

**Java (via [mise](https://mise.jdx.dev/)):**
```bash
mise install            # installs the pinned Java 21 (mise.toml)
java -version           # should show 21.x
```

**Database (local Postgres via Docker):**
```bash
docker compose up -d    # starts Postgres (db: starter, port 5432, user/pass: postgres)
```
Schema is created by Flyway migrations on the first `mvn quarkus:dev` — no manual SQL needed.

**Credentials** — create a `.env` at the project root (gitignored, never commit real values):
```
QUARKUS_DATASOURCE_USERNAME=postgres
QUARKUS_DATASOURCE_PASSWORD=postgres
```

**Run / build:**
```bash
mvn quarkus:dev         # dev mode, live reload → http://localhost:8080
mvn spotless:apply      # format before committing
mvn clean verify        # format check + ArchUnit + tests (must pass)
```

Useful in dev mode: Swagger UI `/q/swagger-ui`, health `/q/health`.

---

## 2. Working with AI agents (Claude Code)

You may use AI to write code — but you own what you hand off. Non-negotiable:

- **[CLAUDE.md](CLAUDE.md) is the contract.** Claude Code reads it automatically; make sure your prompts stay within it. If an agent proposes something that contradicts it, the file wins.
- **Mirror the `applicant` module** and `common/*` — don't let the agent invent new structure.
- **Review before you commit.** Read the diff, run `mvn clean verify` locally, and confirm the handoff report (CLAUDE.md §18). "The AI wrote it" is not a review.
- Never let an agent weaken a check (tests, ArchUnit, Spotless) or touch secrets to go green.

---

## 3. Deployment

Deployment is **Kamal** (`config/deploy.*.yml`, `.github/workflows/deploy-*.yml`). The service runs
behind the API gateway (APISix), which handles authentication and authorization.

**Coordinate with DevOps** for the actual rollout — DNS, TLS/hosting, registry access, target servers,
and environment secrets are owned by them. Don't point the deploy configs at real infrastructure on your own.
