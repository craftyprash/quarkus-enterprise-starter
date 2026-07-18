# Project Setup

---

## Prerequisites

| Tool       | Version | Purpose                 |
|------------|---------|-------------------------|
| mise       | latest  | Java version management |
| Maven      | 3.9+    | Build tool              |
| Git        | 2.x     | Version control         |
| Docker     | latest  | Container builds        |
| PostgreSQL | 15+     | Database (local dev)    |

---

## Install Java 21

Java 21 LTS is managed via [mise](https://mise.jdx.dev/). The `mise.toml` at the project root pins the version:

```bash
mise install
java -version   # should show 21.x
```

If `java -version` shows an older version, activate mise in your shell:

```bash
eval "$(mise activate bash)"   # or zsh
```

---

## Clone and Build

```bash
git clone <repo-url>
cd quarkus-starter-enterprise
mvn clean verify
```

This runs in order:

1. **initialize** — configures `git core.hooksPath` to `.githooks/`
2. **validate** — Spotless checks formatting (fails build if unformatted)
3. **compile** — Java 21
4. **test** — ArchUnit (18 rules) + integration tests against H2
5. **package** — Quarkus fast-jar build

---

## Database Setup

Start a local Postgres (creates the `starter` database automatically):

```bash
docker compose up -d
```

The `.env` file at the project root holds database credentials (gitignored):

```
QUARKUS_DATASOURCE_USERNAME=postgres
QUARKUS_DATASOURCE_PASSWORD=postgres
```

The JDBC URL is in `application-dev.properties` (committed), not in `.env`.

**Schema is managed by Flyway** — versioned SQL migrations in `src/main/resources/db/migration/`.
They apply on startup in dev/staging/production (`quarkus.flyway.migrate-at-start=true`); Hibernate
never creates tables (`database.generation=none`). Tests use H2 + Hibernate `drop-and-create`.

---

## Run in Dev Mode

```bash
mvn quarkus:dev
```

| URL                                  | Purpose            |
|--------------------------------------|--------------------|
| http://localhost:8080/q/swagger-ui   | Swagger UI         |
| http://localhost:8080/q/health       | Health checks      |
| http://localhost:8080/q/metrics      | Prometheus metrics |
| http://localhost:8080/q/dev-ui       | Quarkus Dev UI     |

---

## Docker Build

Multi-stage Dockerfile at project root:

```bash
docker build -t starter .
docker run -p 8080:8080 --env-file .env starter
```

Stage 1: `maven:3.9-eclipse-temurin-21` (build)
Stage 2: `eclipse-temurin:21-jre-alpine` (runtime, non-root user)

---

## Configuration Strategy

| File                                | Location             | Contains                                  | Committed |
|-------------------------------------|----------------------|-------------------------------------------|-----------|
| `application.properties`            | `src/main/resources` | Shared config (all environments)          | Yes       |
| `application-dev.properties`        | `src/main/resources` | Local dev datasource (docker-compose DB)  | Yes       |
| `application-test.properties`       | `src/main/resources` | H2 datasource, schema generation          | Yes       |
| `application-staging.properties`    | `src/main/resources` | Staging profile (non-secret)              | Yes       |
| `application-production.properties` | `src/main/resources` | Production profile (non-secret)           | Yes       |
| `.env`                              | project root         | Secrets only (credentials)                | No        |

Staging/production DB URL and credentials come from the **environment** at deploy time (Kamal
`env.clear` / `env.secret`), never from a committed file. See CLAUDE.md §11.

### Why .env holds only secrets

Environment variables from `.env` have the **highest priority** in Quarkus config resolution. No properties file can override them. So:

- `.env` → only credentials (username, password) that are harmless if leaked to test (H2 accepts any credentials)
- `application-dev.properties` → local datasource overrides
- `application-test.properties` → H2 datasource, schema generation

This way `application-test.properties` cleanly overrides `application-dev.properties` without fighting `.env`.

---

## Git Hooks

The `.githooks/commit-msg` hook enforces **Conventional Commits**. Maven auto-configures `git core.hooksPath` during the `initialize` phase.

```bash
# ✅ Good
git commit -m "feat(applicant): add email validation"

# ❌ Rejected
git commit -m "added stuff"
```

---

## Formatting

Spotless runs during the `validate` phase. The build fails on unformatted code.

```bash
mvn spotless:apply    # fix formatting before committing
```
