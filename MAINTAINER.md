# MAINTAINER.md

# Maintainer Guide

Infrastructure decisions, known workarounds, and upgrade guidance. Read this before upgrading any dependency.

---

## Stack Versions

| Component          | Version | Why                                                    |
|--------------------|---------|--------------------------------------------------------|
| Java               | 25 LTS  | Latest LTS, managed via `mise.toml`                   |
| Quarkus            | 3.23.0  | First version with ASM 9.8 (supports class file v69)  |
| Google Java Format | 1.27.0  | First version compatible with Java 25 javac internals  |
| Spotless           | 2.44.4  | Works with GJF 1.27.0                                 |
| ArchUnit           | 1.4.1   | Supports Java 25 bytecode via ASM 9.8                 |
| Byte Buddy         | 1.15.11 | Bundled by Quarkus — requires experimental flag        |

---

## Java 25 Workarounds

These should be **revisited on every Quarkus upgrade**.

### 1. Byte Buddy Experimental Flag

Byte Buddy does not officially support Java 25 (class file major version 69).

```
Java 25 (69) is not supported by the current version of Byte Buddy
```

Set globally via `.mvn/jvm.config`:

```
-Dnet.bytebuddy.experimental=true
```

Also set in:
- `pom.xml` Surefire `<argLine>` (forked test JVM)
- `Dockerfile` `JAVA_OPTS` (runtime)

**Remove when:** Quarkus bundles a Byte Buddy version that supports Java 25.

### 2. `--add-opens java.base/java.lang=ALL-UNNAMED`

Vert.x thread-local-reset uses deep reflection blocked by Java 24+.

Set in `.mvn/jvm.config` and Surefire `<argLine>`.

**Remove when:** Vert.x no longer requires this access.

### 3. `--enable-preview`

Set in `maven-compiler-plugin`, Surefire `<argLine>`, and `Dockerfile`.

**Remove when:** Preview features in use become stable. Remove from all three locations simultaneously.

### 4. Google Java Format 1.27.0

GJF 1.25.x uses a javac internal API removed in Java 25. Pinned to 1.27.0 in the Spotless plugin config.

**Keep at:** >= 1.27.0.

### 5. Tuple API for Native SQL

Quarkus bytecode transformation on Java 25 fails with `Object[]` array access. All native SQL uses `jakarta.persistence.Tuple` instead. This is the better pattern regardless — keep it.

### 6. UTC Timezone

`-Duser.timezone=UTC` is set in `.mvn/jvm.config`, Surefire `<argLine>`, and `Dockerfile`. Safety net for any code that accidentally uses `LocalDateTime.now()` or `new Date()`.

---

## Architecture Decisions

### Module Contract Pattern

Each module exposes a single interface at its package root (`com.starter.{module}.{Module}Api`). Cross-module communication goes through this interface only. Input/output types are nested records inside the interface — no separate package needed.

This is enforced by ArchUnit:
- Cross-module deps must target module root package
- Classes at module root must be interfaces
- No cross-module access to `internal`, `domain`, or `api` packages

### Panache + QueryRepo (No JOOQ)

Panache for standard CRUD. `QueryRepo` (thin EntityManager wrapper with `Tuple` API) for native SQL projections. JOOQ adds code generation, build pipeline coupling, and a separate DSL — unnecessary when you're comfortable with SQL.

### Liquibase with SQL Changesets

SQL changesets, not YAML/XML. The abstraction adds friction when you already know SQL. SQL is portable, reviewable in PRs, and exact.

### .env for Secrets Only

`.env` environment variables have the highest priority in Quarkus config — no properties file can override them. So `.env` holds only credentials (username, password). Connection URLs and feature flags go in `application-{profile}.properties` where they can be properly overridden per profile.

### Instant over LocalDateTime

`Instant` is unambiguous (always UTC). `LocalDateTime` has no timezone — ambiguous and error-prone. PostgreSQL `TIMESTAMPTZ` pairs with `Instant`. Jackson serializes `Instant` as `"2024-03-14T10:30:00Z"` automatically.

### HTTP Types vs Contract Types

HTTP response records (`ApplicantRes`) may have `@JsonProperty`, `@Schema` annotations. Contract records (`ApplicantApi.Info`) are pure data. The REST resource maps between them. This decouples the HTTP contract from the module contract.

### Hibernate Envers for Audit

Creates `_AUD` tables for `@Audited` entities. Stores data at delete. Disabled in tests (H2 compatibility issues with audit tables).

### JWT + External Permission Service

Authentication via `quarkus-smallrye-jwt`. Permissions are not stored locally — they're fetched from an external service by passing the JWT token. The external service resolves the token's subject/roles and returns a `Map<Resource, Set<Action>>` + data scopes. Cached per user token via `@CacheResult`. `PermissionFilter` populates a `@RequestScoped` `PermissionContext` on every request. Filter skips gracefully when no JWT is present (dev mode, public endpoints). Disabled in test profile (`quarkus.smallrye-jwt.enabled=false`).

### Exception Strategy

Services throw JDK exceptions (`NoSuchElementException`, `IllegalStateException`, `IllegalArgumentException`) + two custom exceptions (`DuplicateException`, `ForbiddenException`). `GlobalExceptionMapper` maps them to HTTP status codes with a standard `ErrorRes(status, error, message)` shape. Services must not depend on `jakarta.ws.rs` — enforced by ArchUnit.

### Integration Layer

External API integrations live in `common/integration/` — not inside business modules. Each integration has a `@RegisterRestClient` interface (HTTP contract) and a `Gateway` wrapper (business-facing API). Modules inject the gateway, never the client.

Current integrations:

| Config key | Client | Gateway | Package |
|---|---|---|---|
| `permission-api` | `PermissionClient` | `PermissionService` | `common.security` |
| `idfc-bank-api` | `IdfcBankClient` | `IdfcBankGateway` | `common.integration.bank` |
| `hdfc-bank-api` | `HdfcBankClient` | `HdfcBankGateway` | `common.integration.bank` |
| `lms-api` | `LmsClient` | `LmsGateway` | `common.integration.lms` |

Why not inside modules: ArchUnit blocks cross-module `internal` access. If bank integration lived in `payment.internal`, a future `refund` module couldn't use it. `common/integration/` is accessible from any module.

### Microcks for External API Mocking

OpenAPI specs for all external APIs live in `src/main/resources/microcks/`. Import them into [Microcks](https://microcks.io/) to get mock endpoints. `application-dev.properties` points all REST clients to the Microcks instance. In tests, all clients point to `localhost:0` (disabled). In production, they point to real URLs.

To add a new external integration:
1. Create `@RegisterRestClient` interface + `Gateway` wrapper in `common/integration/{name}/`
2. Add REST client config in `application.properties`, `application-dev.properties`, `application-test.properties`
3. Add OpenAPI spec in `src/main/resources/microcks/`
4. Import spec into Microcks

---

## Key Files

| File                   | Purpose                                                  |
|------------------------|----------------------------------------------------------|
| `mise.toml`            | Pins Java version                                        |
| `.mvn/jvm.config`      | JVM flags (Byte Buddy, add-opens, UTC timezone)          |
| `.githooks/commit-msg`  | Conventional commit enforcement                          |
| `.env`                 | Local secrets (gitignored)                               |
| `db.sh`                | Liquibase helper (new, status, validate, rollback, etc.) |
| `Dockerfile`           | Multi-stage build                                        |
| `pom.xml`              | All dependency versions and plugin config                |

---

## Upgrade Checklist

1. Bump `<quarkus.platform.version>` in `pom.xml`
2. Run `mvn clean verify`
3. If it passes, try removing workarounds one at a time:
   - Remove `-Dnet.bytebuddy.experimental=true` from `.mvn/jvm.config`, Surefire argLine, Dockerfile
   - Remove `--add-opens` from `.mvn/jvm.config` and Surefire argLine
   - Remove `--enable-preview` from compiler, Surefire, Dockerfile (if no preview features in use)
4. If `mvn spotless:apply` fails, bump GJF version in the `<googleJavaFormat>` block
5. If ArchUnit fails with "Unsupported class file major version", bump `<archunit.version>`
6. Update this document

---

## Observability Endpoints

| Endpoint           | Extension                                |
|--------------------|------------------------------------------|
| `/q/health`        | `quarkus-smallrye-health`                |
| `/q/health/live`   | Liveness probes                          |
| `/q/health/ready`  | Readiness probes                         |
| `/q/metrics`       | `quarkus-micrometer-registry-prometheus` |
| `/q/swagger-ui`    | `quarkus-smallrye-openapi`               |
| `/q/openapi`       | OpenAPI spec (JSON/YAML)                 |

---

## ArchUnit Rules (18)

| # | Category | Rule |
|---|----------|------|
| 1 | Layer | `api` ↛ `domain` |
| 2 | Layer | `domain` ↛ `api` |
| 3 | Layer | `domain` ↛ `internal` |
| 4 | Cross-module | No cross-module `internal` access |
| 5 | Cross-module | No cross-module `domain` access (from `internal`) |
| 6 | Cross-module | No cross-module `domain` access (from `domain`) |
| 7 | Cross-module | No cross-module `api` access |
| 8 | Contract | Cross-module deps at module root must be interfaces |
| 9 | Contract | Top-level classes at module root must be interfaces |
| 10 | HTTP isolation | `internal` ↛ `jakarta.ws.rs` |
| 11 | Naming | `..api.request..` classes end with `Req` |
| 12 | Naming | `..api.response..` classes end with `Res` |
| 13 | Naming | `@Path` only in `..api..` (excludes `@RegisterRestClient`) |
| 14 | Naming | `@Path` classes end with `Resource` (excludes `@RegisterRestClient`) |
| 15 | Naming | `PanacheRepository` impls end with `Repo` |
| 16 | Naming | `PanacheRepository` impls are `@ApplicationScoped` |
| 17 | Naming | `@Entity` in `..domain..` |
| 18 | Naming | `@Entity` extends `BaseEntity` or `BaseUuidEntity` |
