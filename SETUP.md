# SETUP.md

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

## Install Java 25

Java 25 LTS is managed via [mise](https://mise.jdx.dev/). The `mise.toml` at the project root pins the version:

```bash
mise install
java -version   # should show 25.x
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
3. **compile** — Java 25 with `--enable-preview`
4. **test** — ArchUnit (18 rules) + integration tests against H2
5. **package** — Quarkus fast-jar build

---

## Database Setup

```sql
CREATE DATABASE starter;
```

The `.env` file at the project root holds database credentials (gitignored):

```
QUARKUS_DATASOURCE_USERNAME=postgres
QUARKUS_DATASOURCE_PASSWORD=postgres
```

The JDBC URL is in `application-dev.properties` (committed), not in `.env`.

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

Stage 1: `maven:3.9-eclipse-temurin-25` (build)
Stage 2: `eclipse-temurin:25-jre-alpine` (runtime, non-root user)

---

## Configuration Strategy

| File                          | Location             | Contains                          | Committed |
|-------------------------------|----------------------|-----------------------------------|-----------|
| `application.properties`      | `src/main/resources` | Shared config (all environments)  | Yes       |
| `application-dev.properties`  | `src/main/resources` | Dev JDBC URL, Microcks URLs       | Yes       |
| `application-test.properties` | `src/main/resources` | H2 datasource, skip Liquibase, disable JWT/scheduler | Yes       |
| `.env`                        | project root         | Secrets only (credentials)        | No        |

### Why .env holds only secrets

Environment variables from `.env` have the **highest priority** in Quarkus config resolution. No properties file can override them. So:

- `.env` → only credentials (username, password) that are harmless if leaked to test (H2 accepts any credentials)
- `application-dev.properties` → JDBC URL, logging levels, feature flags
- `application-test.properties` → H2 datasource, schema generation, disabled Envers

This way `application-test.properties` cleanly overrides `application-dev.properties` without fighting `.env`.

---

## Database Migrations

Liquibase runs on startup (`quarkus.liquibase.migrate-at-start=true`).

Changesets are **SQL files** in `src/main/resources/db/changelog/`:

```
db/changelog/
├── db.changelog-master.yaml    ← includes SQL files in order
├── 001-create-applicant.sql
├── 002-create-drawdown.sql
└── ...
```

Naming: `NNN-short-description.sql`

### db.sh — migration helper

Use `db.sh` for all Liquibase operations:

```bash
# Create a new table changeset with audit table
./db.sh new create-refund --audit

# Create a non-table changeset (index, alter, etc.)
./db.sh new add-payment-status-index

# Check what's pending
./db.sh status

# Validate changelog syntax
./db.sh validate

# Rollback last N changesets
./db.sh rollback 1

# See applied changesets
./db.sh history

# Open psql shell
./db.sh psql
```

The `new` command auto-generates the SQL file with correct boilerplate and registers it in `db.changelog-master.yaml`. For `create-*` names, it generates `CREATE TABLE` + `TIMESTAMPTZ` columns. With `--audit`, it adds the `_AUD` table. For other names, it generates a blank changeset.

DB connection reads from `.env` (credentials) with defaults for host/port/dbname.

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

## External API Mocking (Microcks)

All external APIs (bank, LMS, permissions) are mocked via [Microcks](https://microcks.io/) in dev mode.

### Start Microcks

```bash
docker run -p 8585:8080 quay.io/microcks/microcks-uber:latest
```

Open http://localhost:8585 and import the OpenAPI specs from `src/main/resources/microcks/`:

| Spec file | Mock base URL |
|---|---|
| `permission-api-0.1.0.json` | `http://localhost:8585/rest/Permission+API/0.1.0` |
| `idfc-bank-api-0.1.0.json` | `http://localhost:8585/rest/IDFC+Bank+API/0.1.0` |
| `hdfc-bank-api-0.1.0.json` | `http://localhost:8585/rest/HDFC+Bank+API/0.1.0` |
| `lms-api-0.1.0.json` | `http://localhost:8585/rest/LMS+API/0.1.0` |

`application-dev.properties` already points all REST clients to these URLs.

### Verify mocks

```bash
# Permissions
curl -X GET 'http://localhost:8585/rest/Permission+API/0.1.0/permissions' \
  -H 'Accept: application/json' \
  -H 'Authorization: Bearer any-token-here'

# IDFC disburse
curl -X POST 'http://localhost:8585/rest/IDFC+Bank+API/0.1.0/api/v1/disburse' \
  -H 'Content-Type: application/json' \
  -d '{"paymentId": 1, "amount": 50000}'
```

---

## Formatting

Spotless runs during the `validate` phase. The build fails on unformatted code.

```bash
mvn spotless:apply    # fix formatting before committing
```
