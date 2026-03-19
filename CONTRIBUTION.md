# Engineering Contribution Guide

This document defines **how we write code** in this repository. Every example here is from the actual codebase — follow the patterns exactly.

---

# Module Structure

Each business capability is a **module** — a top-level package under `com.starter`.

```
com.starter.applicant/
  ApplicantApi.java              ← contract interface (the only public face)
  api/
    ApplicantResource.java       ← REST endpoints
    request/
      CreateApplicantReq.java    ← HTTP input (validated)
    response/
      ApplicantRes.java          ← HTTP output (JSON shape)
  internal/
    ApplicantService.java        ← business logic, implements ApplicantApi
    ApplicantRepo.java           ← Panache repository (CRUD)
    ApplicantQueryRepo.java      ← native SQL via Tuple API
  domain/
    Applicant.java               ← JPA entity
```

Every module follows this layout. No exceptions.

---

# Package Responsibilities

| Package | Contains | Does NOT contain |
|---|---|---|
| Module root | Contract interface only | Concrete classes, services, entities |
| `api` | REST resource, request/response records | Business logic, repository calls, transactions |
| `internal` | Service, repositories, processors | REST annotations, HTTP concerns |
| `domain` | JPA entities, domain invariants | CDI, REST, remote calls |

---

# Module Contract

Every module exposes a **single interface** at its package root. This is the **only** way other modules interact with yours.

```java
package com.starter.applicant;

public interface ApplicantApi {

    record Info(Long id, String name, String email, String status, Instant createdAt) {}

    record Summary(Long id, String name, String status) {}

    Info findById(Long id);

    List<Summary> listActive();
}
```

Rules:
- Lives at the **module root** — `com.starter.applicant.ApplicantApi`
- Input/output types are **nested records** inside the interface
- Contract types carry **no annotations** — no JSON, no validation, pure data
- The service in `internal/` implements this interface

When another module needs your capability:

```java
@Inject ApplicantApi applicantApi;   // ← interface, never the concrete service
```

### What's allowed across modules

| ✅ Allowed | ❌ Not allowed |
|---|---|
| `other.internal` → `ApplicantApi` | `other.internal` → `applicant.internal.*` |
| `other.internal` → `ApplicantApi.Info` | `other.internal` → `applicant.domain.*` |
| | `other.internal` → `applicant.api.*` |

The build enforces this — you'll get an ArchUnit failure if you reach into another module's internals.

---

# REST Layer

The resource maps between HTTP types and contract types. It owns the HTTP shape — status codes, JSON structure, validation.

```java
package com.starter.applicant.api;

@Path("/applicants")
public class ApplicantResource {

    @Inject ApplicantService service;

    @POST
    public Response create(@Valid CreateApplicantReq req) {
        var res = service.create(req);
        return Response.status(201).entity(res).build();
    }

    @GET
    @Path("/{id}")
    public ApplicantRes findById(@PathParam("id") Long id) {
        var info = service.findById(id);
        return new ApplicantRes(info.id(), info.name(), info.email(), info.status(), info.createdAt());
    }
}
```

### HTTP Request — validated, annotated

```java
package com.starter.applicant.api.request;

public record CreateApplicantReq(@NotBlank String name, @NotBlank @Email String email) {}
```

### HTTP Response — JSON shape for the client

```java
package com.starter.applicant.api.response;

public record ApplicantRes(Long id, String name, String email, String status, Instant createdAt) {}
```

Why separate HTTP types from contract types? The contract is stable across modules. The HTTP shape can evolve independently — add `@JsonProperty`, `@Schema`, pagination wrappers — without touching the contract.

---

# Entity and Base Classes

Every entity **must** extend one of the two base classes:

```java
package com.starter.applicant.domain;

@Entity
public class Applicant extends BaseEntity {

    @Column(nullable = false)
    public String name;

    @Column(nullable = false, unique = true)
    public String email;

    @Column(nullable = false)
    public String status = "ACTIVE";

    protected Applicant() {}

    public Applicant(String name, String email) {
        this.name = Objects.requireNonNull(name, "name required");
        this.email = Objects.requireNonNull(email, "email required");
    }
}
```

### Base class choice

| Base class | ID type | When to use |
|---|---|---|
| `BaseEntity` | `Long` (BIGSERIAL) | Most entities — auto-increment, simple |
| `BaseUuidEntity` | `UUID` | External-facing IDs, distributed systems |

Both provide `id`, `createdAt` (`@CreationTimestamp`), and `updatedAt` (`@UpdateTimestamp`) automatically.

### Entity rules

- `@Entity` on every entity
- Protected no-arg constructor for JPA
- Public constructor enforces domain invariants with `Objects.requireNonNull`
- Public fields (Panache style) — no getters/setters
- Lives in `domain/` package only
- References other modules by **ID** (`Long applicantId`), never by entity reference

### Domain dependencies

| ✅ Allowed | ❌ Not allowed |
|---|---|
| `applicant.domain` → `common.domain` (BaseEntity, BaseUuidEntity) | `applicant.domain` → `other.domain` |
| `applicant.domain` → JDK / Jakarta Persistence | `applicant.domain` → any other module's domain |

---

# Naming Conventions

| What | Suffix | Location | Example |
|---|---|---|---|
| Module contract | `Api` | Module root | `ApplicantApi` |
| REST endpoint | `Resource` | `api/` | `ApplicantResource` |
| HTTP input | `Req` | `api/request/` | `CreateApplicantReq` |
| HTTP output | `Res` | `api/response/` | `ApplicantRes` |
| Panache repository | `Repo` | `internal/` | `ApplicantRepo` |
| Native SQL repository | `QueryRepo` | `internal/` | `ApplicantQueryRepo` |
| Business logic | `Service` | `internal/` | `ApplicantService` |
| JPA entity | (no suffix) | `domain/` | `Applicant` |

### Names we don't use

| ❌ Avoid | ✅ Use instead |
|---|---|
| Controller | Resource |
| Manager | Service |
| Impl | (omit — the service IS the implementation) |
| DTO | Req / Res (HTTP) or nested record (contract) |
| Repository | Repo |

---

# Repository Patterns

### Panache Repo — CRUD and simple queries

```java
package com.starter.applicant.internal;

@ApplicationScoped
public class ApplicantRepo implements PanacheRepository<Applicant> {

    public Optional<Applicant> findByEmail(String email) {
        return find("email", email).firstResultOptional();
    }
}
```

### QueryRepo — native SQL with Tuple

For projections, aggregations, or anything beyond Panache CRUD:

```java
package com.starter.applicant.internal;

@ApplicationScoped
public class ApplicantQueryRepo {

    @Inject EntityManager em;

    public List<Summary> findActiveSummaries() {
        var sql = "SELECT id, name, status FROM applicant WHERE status = ?1";
        List<Tuple> rows =
                em.createNativeQuery(sql, Tuple.class).setParameter(1, "ACTIVE").getResultList();
        return rows.stream()
                .map(t -> new Summary(
                        t.get(0, Long.class),
                        t.get(1, String.class),
                        t.get(2, String.class)))
                .toList();
    }
}
```

Always use `Tuple` — never `Object[]`.

---

# DateTime

- `Instant` everywhere — never `LocalDateTime`
- Database columns: `TIMESTAMPTZ`
- JVM runs with `-Duser.timezone=UTC`
- Jackson serializes as: `"2024-03-14T10:30:00Z"`

---

# Modern Java

Use these where appropriate — no Lombok in this project.

| Pattern | Use for |
|---|---|
| `record` | DTOs, contract types, value objects |
| `var` | Local variables |
| `switch` expressions | Pattern matching |
| `instanceof` pattern matching | Type checks |
| `Optional` | Query results that may be absent |
| Text blocks (`"""`) | SQL, JSON |
| `sealed` | Domain hierarchies |

---

# Logging

```java
private static final Logger log = LoggerFactory.getLogger(ApplicantService.class);

log.info("Applicant created id={}", applicant.id);
```

- `info` for state changes (created, updated, deleted)
- `debug` for diagnostics
- Never log sensitive data or full request payloads

---

# Exceptions

Services must **not** depend on `jakarta.ws.rs` — this is enforced by ArchUnit. Throw JDK exceptions or custom exceptions in `common.exception`.

| Exception | When to use | HTTP status (mapped by GlobalExceptionMapper) |
|---|---|---|
| `NoSuchElementException` | Entity not found | 404 |
| `IllegalArgumentException` | Bad input, invalid config | 422 |
| `IllegalStateException` | Wrong state for operation | 409 |
| `DuplicateException` | Uniqueness violation | 409 |
| `ForbiddenException` | Missing permission or data scope | 403 |
| `ConstraintViolationException` | Jakarta validation failure | 400 |

```java
var applicant = repo.findByIdOptional(id)
        .orElseThrow(() -> new NoSuchElementException("Applicant not found"));

repo.findByEmail(req.email())
        .ifPresent(a -> { throw new DuplicateException("Email already exists"); });
```

The `GlobalExceptionMapper` maps these to a standard error response:

```json
{"status": 404, "error": "NOT_FOUND", "message": "Applicant not found"}
```

All error responses follow this shape. Success responses are the `Res` record directly — no envelope.

---

# Authorization

Authorization is handled via JWT + an external permission service. Permissions are loaded once per request and cached per user.

### Request flow

```
HTTP Request → JWT Authentication → PermissionFilter → PermissionContext → Endpoint → Service
```

1. `PermissionFilter` extracts the JWT, calls the external permission API (cached), populates `PermissionContext`
2. Endpoint-level checks via `@RequiresPermission` annotation
3. Service-level record checks via `PermissionContext` injection

### Permission model

Permissions use a `Map<Resource, Set<Action>>` structure:

```json
{
  "permissions": {
    "applicant": ["view", "create", "update", "delete"]
  },
  "scopes": {
    "branch": ["BR001", "BR002"]
  }
}
```

### Endpoint-level — `@RequiresPermission`

```java
@GET
@RequiresPermission(resource = "applicant", action = "view")
public ApplicantRes findById(@PathParam("id") Long id) {
    return toRes(service.findById(id));
}
```

### Service-level — record checks

```java
@Inject PermissionContext permissionContext;

public Info findById(Long id) {
    var applicant = repo.findByIdOptional(id)
            .orElseThrow(() -> new NoSuchElementException("Applicant not found"));

    if (!permissionContext.scope("branch").contains(applicant.branchCode)) {
        throw new ForbiddenException("No access to this branch");
    }

    return toInfo(applicant);
}
```

### PermissionContext API

```java
context.has("applicant", "view")              // exact check
context.hasAny("applicant", "view", "update") // any of these actions
context.scope("branch")                       // returns Set<String> of allowed values
context.userId()                              // current user ID from JWT subject
```

### Where things live

| Class | Package | Purpose |
|---|---|---|
| `PermissionContext` | `common.security` | Request-scoped permission holder |
| `PermissionFilter` | `common.security` | JAX-RS filter, loads permissions from JWT |
| `PermissionClient` | `common.security` | REST client to external permission API |
| `PermissionService` | `common.security` | Cached layer over the client |
| `RequiresPermission` | `common.security` | Interceptor binding annotation |
| `PermissionInterceptor` | `common.security` | Checks annotation against context |
| `ForbiddenException` | `common.exception` | Authorization failure → 403 |

---

# Response Conventions

- `POST` → `Response` (for 201 status + body)
- `GET`, `PUT`, `DELETE` → return the typed record directly (200 implicit)
- Error shape → standard `ErrorRes` from `GlobalExceptionMapper`

```java
@POST
public Response create(@Valid CreateApplicantReq req) {
    var res = service.create(req);
    return Response.status(201).entity(res).build();
}

@GET
@Path("/{id}")
public ApplicantRes findById(@PathParam("id") Long id) {
    return toRes(service.findById(id));
}
```

---

# Transaction Rules

Transactions live **only** in `internal/` services.

```java
@Transactional
public ApplicantRes create(CreateApplicantReq req) {
    repo.persist(applicant);
}
```

Never call external APIs inside a transaction — a slow or failed remote call holds the DB connection open.

---

# Git Commits

Conventional commits enforced by git hook:

```
type(scope): description
```

Types: `feat` `fix` `docs` `style` `refactor` `perf` `test` `build` `ci` `chore` `revert`

```bash
git commit -m "feat(applicant): add email validation"
```

---

# Adding a New Module

1. Create package `com.starter.{module}`
2. Add contract interface: `{Module}Api.java` with nested records
3. Create subpackages: `api/`, `api/request/`, `api/response/`, `internal/`, `domain/`
4. Entity extends `BaseEntity` or `BaseUuidEntity`, annotated with `@Entity`
5. Repo implements `PanacheRepository<Entity>`, annotated with `@ApplicationScoped`
6. Service implements `{Module}Api`, annotated with `@ApplicationScoped`
7. Resource at `@Path("/{modules}")` in `api/`, maps contract ↔ HTTP types
8. Run `mvn clean verify` — ArchUnit catches any violations

---

# Quick Reference

If a class **starts a transaction, orchestrates workflows, or implements a module contract** — it belongs in `internal/`.

If a class **defines the HTTP shape** — it belongs in `api/`.

If a class **is the data** — it belongs in `domain/`.

If a class **is the boundary** — it's the interface at the module root.
