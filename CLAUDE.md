# CLAUDE.md — Build Contract for AI Agents

You are writing code for a **regulated NBFC lending system**. Money moves, PII is stored, every change is auditable. Code that "works" but breaks a rule below is a **defect**, not a contribution.

This is a **starter template**. Mirror the reference modules — **`applicant`** (simple CRUD + pagination), **`drawdown`** (cross-module orchestration), **`payment`** (money + outbox processors) — and `com.starter.common.*`. Pick whichever is closest to your task; don't invent new structure. Read this file fully before writing code. When this file and your instinct disagree, this file wins.

---

## 0. Prime directives

1. **Never invent.** Do not fabricate config keys, table names, endpoints, permission names, class names, or library methods. If something isn't in the code, say so — don't assume it exists. This repo is small: `grep` before you claim.
2. **Follow the existing pattern.** Mirror the closest reference module — `applicant`, `drawdown`, or `payment` — and `com.starter.common.*`. Match it exactly. Novel structure is a defect even when it looks "better".
3. **Never weaken a check to go green.** No deleting/skipping/`@Disabled` tests, no relaxing an ArchUnit rule, no `-DskipTests`, no `@SuppressWarnings`, no `spotless:off`. If a check fails, fix the code.
4. **Never touch secrets.** Do not read, print, log, commit, or hardcode anything from `.env`, keystores, tokens, or credentials.
5. **Never guess a business rule.** Interest, fees, eligibility, limits, statuses, rounding — if it isn't written in code or the ticket, **stop and ask**.
6. **Report what you don't know.** End every task with explicit assumptions and unverified areas (§18). Silence is treated as a lie.

### Stop and ask before you do any of these
- Change an existing REST path, request/response shape, or status code.
- Add authentication/authorization logic to this app — that lives at the API gateway (§5). Also don't touch `GlobalExceptionMapper`.
- Change money handling, rounding, or transaction boundaries.
- Add or change an external API integration or its contract.
- Touch a PII field (PAN, Aadhaar, bank account, phone, email, address, DOB).
- Change the DB schema of an existing table, or drop a column.
- Do a bulk refactor, rename, or reformat of files unrelated to the task.

---

## 1. Stack — fixed

| | |
|---|---|
| Language | **Java 25 (LTS)** on **Quarkus 3.33 LTS** — no preview features, no `--enable-preview`, no build workarounds |
| Framework | **Quarkus** (REST + Jackson, Hibernate ORM + Panache, Hibernate Validator) |
| DB | **PostgreSQL** everywhere — dev, prod, and tests (throwaway Postgres via Quarkus Dev Services / Testcontainers) |
| Build | **Maven** + Spotless (Google Java Format, AOSP) + ArchUnit |
| Java version | **mise** — `mise.toml` pins it |
| Auth | **None in-app** — authentication & authorization are handled by the **API gateway (APISix)** in front of the service (§5) |
| Deploy | **Kamal** — Docker image, `config/deploy.*.yml`, `.github/workflows/deploy-*.yml` (§11) |

Do not upgrade Java, Quarkus, or any version as a side effect of a task.

---

## 2. Dependencies — allowed, with guardrails

Adding a library is **fine** when the JDK and existing Quarkus extensions genuinely can't do the job. You are **not** restricted to what's in `pom.xml` today. But a dependency is a permanent liability, so:

**Allowed freely**
- Official Quarkus extensions (`io.quarkus:quarkus-*`) — always prefer these; they fit the build and native image.
- Well-maintained, permissively licensed libraries (**Apache-2.0, MIT, BSD, EPL, MPL-2.0**).

**Forbidden — do not add**
- **Copyleft / viral licenses: GPL, AGPL, LGPL, SSPL, CC-BY-NC, or any "commercial-use-restricted" license.** This is a compliance line, not a preference.
- Abandoned or unmaintained libraries (no release in ~2 years, known unpatched CVEs, single maintainer with no activity).
- A library that duplicates something Quarkus/JDK already provides (don't add Guava for `List.of`, don't add a second JSON or HTTP client).
- **Lombok, MapStruct, or any compile-time/bytecode-magic dependency** — they fight the build and hide behavior. Use records, plain constructors, and hand-written mappers.

**Before adding any dependency, state in your handoff:** what it's for, why the JDK/Quarkus can't do it, its license, and its maintenance status. If you can't confirm the license is permissive, **don't add it — ask.**

Never add a dependency to make a test pass or to work around a guardrail.

---

## 3. Module layout — mandatory

Every business capability is a top-level package under `com.starter`, shaped like `applicant`:

```
com.starter.applicant/
  ApplicantApi.java          ← contract interface — the ONLY public face
  api/                       ← Resource, request/ (Req), response/ (Res)
  internal/                  ← Service, Repo, QueryRepo, processors
  domain/                    ← JPA entities
```

Shared infrastructure lives in `com.starter.common.*` (`api`, `query`, `domain`, `exception`, `integration`) — never inside a module's `internal/`.

| Package | Contains | Must NOT contain |
|---|---|---|
| Module root | Contract interface only | Classes, services, entities |
| `api` | Resource, `Req`/`Res` records | Business logic, repo calls, `@Transactional` |
| `internal` | Service, Repo, QueryRepo, processors | `jakarta.ws.rs` imports |
| `domain` | Entities, invariants | CDI, REST, remote calls |

**Cross-module access is only through the other module's `{Module}Api` interface** — never `x.internal.*`, `x.domain.*`, or `x.api.*`. Reference other modules **by ID** (`Long applicantId`), never by entity reference. ArchUnit (18 rules, `ArchitectureTest.java`) fails the build on violations — fix the code, never the rule.

### Naming

| What | Suffix | Location |
|---|---|---|
| Module contract | `Api` | module root |
| REST endpoint | `Resource` | `api/` |
| HTTP input | `Req` | `api/request/` |
| HTTP output | `Res` | `api/response/` |
| Panache repository | `Repo` | `internal/` |
| Native-SQL repository | `QueryRepo` | `internal/` |
| Business logic | `Service` | `internal/` |
| JPA entity | *(no suffix)* | `domain/` |
| REST client | `Client` | `common/` (a `@RegisterRestClient` interface you add — §12) |
| Integration gateway | `Gateway` | `common/integration/` |
| Scheduled processor/poller | `Processor` / `Poller` | `internal/` |

Never use: `Controller`, `Manager`, `Impl`, `DTO`, `Util`, `Helper`, `Repository`.

### Contract interface
Nested records, **no annotations** (no JSON, no validation). HTTP shapes (`Req`/`Res`) are separate types in `api/`.

```java
public interface ApplicantApi {
    record Info(Long id, String name, String email, String status, Instant createdAt) {}
    record Summary(Long id, String name, String status) {}
    Info findById(Long id);
    PageRes<Summary> listActive(int page, int size, String sort, String order);
}
```

---

## 4. REST layer

```java
@Path("/api/v1/applicants")
public class ApplicantResource {
    @Inject ApplicantService service;

    @POST
    public Response create(@Valid CreateApplicantReq req) {
        var res = service.create(req);
        return Response.status(201).entity(res).build();
    }

    @GET @Path("/{id}")
    public ApplicantRes findById(@PathParam("id") Long id) { return toRes(service.findById(id)); }

    @GET
    public PageRes<ApplicantRes> listActive(
            @QueryParam("page") @DefaultValue("1") @Min(1) int page,
            @QueryParam("size") @DefaultValue("10") @Min(1) @Max(100) int size,
            @QueryParam("sort") @DefaultValue("id") String sort,
            @QueryParam("order") @DefaultValue("asc") String order) {
        var p = service.listActive(page, size, sort, order);
        return PageRes.of(p.content().stream().map(this::toRes).toList(), p.page(), p.size(), p.totalElements());
    }
}
```

- `POST` → `Response` (201 + body). `GET`/`PUT`/`DELETE` → return the typed record directly.
- Every `Req` field is fully constrained: `@NotNull`, `@NotBlank`, `@Positive`, `@Size`, `@Digits`, `@Pattern`, `@Email`. **An unvalidated field is a security bug.** `@Valid` on every body parameter; constrain query params too (`@Min`/`@Max` on `page`/`size`).
- Never accept an entity or a contract record as the HTTP body.
- Never accept a client-supplied `id`, `status`, `userId`, `createdAt`, or any amount that should be server-derived. Keep `Req` records minimal — that's what prevents mass-assignment.
- The `Res` record must not expose internal IDs the caller isn't entitled to, full PII, or fields beyond what the endpoint needs.
- **Errors come only from the `common/exception` mappers** (`GlobalExceptionMapper` + `ValidationExceptionMapper`). Never build error JSON by hand; never leak stack traces, SQL, or vendor messages to the client.

### URI conventions

- **Version and pluralise:** `/api/v1/applicants`. Nouns, not verbs — the HTTP method is the verb (`GET`/`POST`/`PUT`/`DELETE /api/v1/applicants[/{id}]`). Never `/getApplicant`, `/api/v1/applicants/{id}/approve` → model state changes as a `PUT`/`POST` on a sub-resource or a status field, not an action verb.
- **Nest for relationships:** `/api/v1/applicants/{applicantId}/documents`.
- **Query params for filter/sort/pagination:** `/api/v1/applicants?status=active&sort=name&order=asc&page=1&size=10`. Never put filters in the path.

### Pagination — the house shape

List endpoints page with `page` (**1-based** — page 1 is the first page), `size`, `sort`, `order` (`asc`/`desc`) and return `common.api.PageRes<T>`:

```json
{ "content": [ ... ], "page": 1, "size": 10, "totalElements": 21, "totalPages": 3 }
```

- Sensible defaults (`page=1`, `size=10`), `@Min(1)` on `page`, and a hard `@Max` on `size`.
- **`sort` maps through a fixed allow-list to a real column** before it touches SQL — never interpolate the raw value (see `ApplicantQueryRepo.SORT_COLUMNS`, §6).

### Response shape — bare success, org-style errors

- **Success** = the bare `Res` record (or `PageRes<Res>`). No `{ "status": "success", "data": … }` envelope — the org guide's success wrapper is **not** used here.
- **Errors** = the `common/exception` mappers only, following the org guide's error structure:

```json
{ "status": "error", "message": "Validation failed",
  "errors": [ { "field": "email", "message": "must be a valid email" } ] }
```

`errors` carries field-level detail for validation failures (400) and is `[]` otherwise; the HTTP status conveys the category. Never build error JSON by hand.

Follow every other part of the org REST guide (URIs, methods, pagination params, status codes).

### Status codes

`200` OK · `201` Created (POST, + body) · `400` validation · `404` not found · `409` conflict/wrong state · `422` bad input · `500` generic. `401`/`403` are the **gateway's** job (§5), not this app's.

---

## 5. Trust boundary — the app sits behind APISix

This service runs **behind the API gateway (APISix)**. Authentication and authorization happen **at the gateway**, not here.

- **Do not add in-app auth.** No JWT parsing, no permission filter, no `@RolesAllowed`/`@RequiresPermission`, no auth interceptor — unless a ticket explicitly asks for it. There is deliberately no `common/security` package in this template.
- **Never trust client input as identity or authority.** If the gateway forwards a caller identity (e.g. a header), treat it as untrusted metadata: validate it, and never let a path/body value decide what the caller may access.
- **Still enforce, in every endpoint:** full input validation (§4), least-data responses (don't return more than the caller needs), and — where a row belongs to a specific tenant/user — an ownership check in the service using the *gateway-provided, validated* identity, never an ID taken straight from the request. Reference: `GatewayIdentityFilter` populates `CallerContext` from the `X-Anchor-Scope` header, and `DrawdownService.findById` rejects out-of-scope reads with `ForbiddenException` (403).
- Do not widen `@PermitAll`, disable TLS, or add a permissive CORS filter to "make auth work" — that's the gateway's job.

---

## 6. Entities and persistence

- Extend `BaseEntity` (Long id) or `BaseUuidEntity` (UUID id — external-facing IDs). Both give `id`, `createdAt`, `updatedAt`.
- `@Entity` on every entity. `protected` no-arg constructor for JPA; public constructor enforces invariants with `Objects.requireNonNull`.
- Public fields (Panache style). No getters/setters, no Lombok.
- `x.domain` may depend on `common.domain` only — never another module's `domain`.

```java
@Entity
public class Applicant extends BaseEntity {
    @Column(nullable = false) public String name;
    @Column(nullable = false, unique = true) public String email;
    @Column(nullable = false) public String status = "ACTIVE";

    protected Applicant() {}
    public Applicant(String name, String email) {
        this.name = Objects.requireNonNull(name, "name required");
        this.email = Objects.requireNonNull(email, "email required");
    }
}
```

**Repositories** — `@ApplicationScoped`, `implements PanacheRepository<Entity>`. Native SQL goes in a `QueryRepo` projected via `Tuple` (never `Object[]`), following the existing `ApplicantQueryRepo`.

**Audit trail:** `payment` and `drawdown` are `@Audited` (Hibernate Envers) — every change is versioned in `*_aud`. Add `@Audited` to entities whose history is regulated/material. Envers audit tables are **not** auto-created under `generation=none`; add their DDL to a Flyway migration (see `V3__envers_audit_tables.sql`).

**SQL safety — hard rule:** every query is parameterized (`?1`, named params, Panache params). **String concatenation / `String.format` / interpolation into any SQL, JPQL, or `LIKE` clause is forbidden.** No dynamic `ORDER BY` or table names from user input — map user input to a fixed allow-list of columns.

> Schema: **Flyway owns it** (`quarkus.hibernate-orm.database.generation=none` — Hibernate never creates or alters tables). If your change needs schema, add a new versioned SQL migration under `src/main/resources/db/migration/` (`V2__add_x.sql`, `V3__…`). **Never edit a migration that has shipped — add the next one.** Migrations apply on startup in dev/staging/production **and in tests** (`quarkus.flyway.migrate-at-start=true`) — tests run against a throwaway Postgres (Dev Services), so the migrations are exercised, not just the entities. Always `TIMESTAMPTZ` for time, `NUMERIC(15,2)` for money, snake_case columns, FK constraints and `NOT NULL` where the domain requires them.

---

## 7. Money, time, types

- Money is **`BigDecimal`** — never `double`/`float`. DB column `NUMERIC(15,2)`.
- Never compare amounts with `==`/`equals()`; use `compareTo`.
- Always specify scale and `RoundingMode` explicitly on any division or rounding. Never rely on defaults.
- Time is **`Instant`** — never `LocalDateTime`. DB `TIMESTAMPTZ`. JVM runs UTC.
- Prefer `record`, `var`, switch expressions, pattern matching, `Optional` (return type only), text blocks, `sealed` — standard Java 25, no preview features.

---

## 8. Transactions and remote calls

- `@Transactional` (from `jakarta.transaction`) lives **only** in `internal/` services.
- **Never make a remote call inside a transaction.** A slow bank/LMS/credit-bureau call holds a DB connection and exhausts the pool.
- For side effects that must survive a crash (disbursement, notifications), use the **outbox pattern** — the `payment` module is the reference. `PaymentService.initiate` writes the `payment` row **and** an `OutboxEvent` row in one transaction and makes **no** remote call. `DisbursementProcessor`/`NeftSettlementPoller` (`@Scheduled`, in `internal/`) then drain the outbox: each **claims** an event in one short transaction, makes the bank/LMS call **outside** any transaction, and writes the terminal status in a second transaction. Never collapse those into one `@Transactional` method — that reintroduces a remote call inside a transaction.

**Anything that moves money must be idempotent under retry:** guard on status, dedupe on the external reference, set an explicit terminal status on every failure path (no silent `continue`), and validate state transitions (`if (!"PENDING".equals(status)) throw new IllegalStateException(...)`) rather than assuming them.

---

## 9. Exceptions

Services must not import `jakarta.ws.rs` (ArchUnit enforced) — never throw `jakarta.ws.rs.NotFoundException` or any `WebApplicationException` from `internal/`. Throw JDK or `common.exception` types; `GlobalExceptionMapper` maps them:

| Throw | When | Status |
|---|---|---|
| `NotFoundException` (`common.exception`) | requested entity doesn't exist | 404 |
| `ConstraintViolationException` (via `@Valid` or `Validator`) | request field shape invalid | 400 + `errors[]` |
| `BusinessValidationException` (`common.exception`) | well-formed but violates a business/semantic rule | 422 |
| `IllegalStateException` | wrong state for the operation | 409 |
| `DuplicateException` | uniqueness violation | 409 |
| `ForbiddenException` | permission / scope failure | 403 |
| *(unmapped)* `IllegalArgumentException`, `NullPointerException`, … | method-contract / programming error | 500 |

- **Not found:** throw `NotFoundException` (`repo.findByIdOptional(id).orElseThrow(() -> new NotFoundException(...))`). A raw `NoSuchElementException` is **not** mapped — an empty collection or misused iterator is a bug and must surface as 500, not a client 404.
- **`IllegalArgumentException` is a programming error, not client input.** It (and `NPE`) are intentionally unmapped → 500. Never throw it to signal bad request data — use Bean Validation (400) or `BusinessValidationException` (422). `"page must be >= 1"` is `@Min(1)`, not a thrown exception.
- **Field validation is normally declarative** — `@NotNull`/`@Positive`/`@Pattern`/… on `Req` + `@Valid` → 400 with `errors[]`. Cross-field rules use a class-level `@AssertTrue`/custom constraint (still declarative).
- **When you must validate in code** (values assembled at runtime, shared allow-lists, conditional rules), reuse Bean Validation via the injected `Validator` — it yields the same 400 `errors[]` shape (see `ApplicantService.validateSort`):

```java
@Inject Validator validator;
var violations = validator.validate(criteria);          // criteria = an annotated record
if (!violations.isEmpty()) throw new ConstraintViolationException(violations);   // → 400 + errors[]
```

Never swallow an exception (`catch (Exception e) {}`), never `printStackTrace()`, never rethrow as a generic `RuntimeException` that loses the cause. Message text must be safe for an end user — no internals, SQL, or upstream vendor text. The `default` branch returns a generic 500 — keep it that way; don't leak the real cause.

---

## 10. Logging and data protection

**Never log:** full request/response bodies, JWTs, headers, passwords, PAN, Aadhaar, bank account/card numbers, OTPs, customer name + amount together, or any external API payload.

**Do log** state changes at `info` with **identifiers only**, via SLF4J parameterized logging:

```java
log.info("Applicant created id={}", applicant.id);
```

- `info` = business state changes; `debug` = diagnostics; `error` = failures with the exception as the last argument.
- Never string-concatenate log messages. Mask any identifier that must appear (`XXXXXX1234`).
- No `System.out.println`. Do not add PII columns without being asked — assume every DB write is retained.

---

## 11. Configuration and secrets

| File | Contains | Committed |
|---|---|---|
| `application.properties` | shared config | yes |
| `application-dev.properties` | local dev JDBC URL (docker-compose DB) | yes |
| `application-test.properties` | Postgres Dev Services, Flyway | yes |
| `application-staging.properties` / `application-production.properties` | profile config (non-secret) | yes |
| `.env` | **credentials only** | **no** |

`.env` env vars have the highest priority in Quarkus and cannot be overridden by any properties file — that is why only harmless, test-safe credentials live there.

- Never hardcode a URL, credential, or key in Java — use `@ConfigProperty` or REST-client `configKey`.
- Never add a real endpoint, password, or token to a committed file, a test, or a comment.
- Never disable TLS verification, add a permissive CORS filter, or widen `@PermitAll`.

### Deployment (Kamal)

Deployment is **Kamal** — Docker image built from the root `Dockerfile`, config in `config/deploy.yml` + per-environment `config/deploy.<staging|production>.yml`, driven by `.github/workflows/deploy-*.yml`.

- Runtime config comes from **environment variables**, set per destination: non-secret values under `env.clear` (e.g. `QUARKUS_PROFILE`, `QUARKUS_DATASOURCE_JDBC_URL`), secrets under `env.secret` (e.g. `QUARKUS_DATASOURCE_USERNAME/PASSWORD`).
- Secrets are read from `.kamal/secrets.<destination>` (gitignored; template in `.kamal/secrets.example`). CI writes them from repository secrets — never commit a real one.
- The proxy health check hits `/q/health/live` (`quarkus-smallrye-health`). Keep that endpoint working.
- Local dev DB: `docker compose up -d` (Postgres), then `mvn quarkus:dev`.

---

## 12. External integrations

The `quarkus-rest-client-jackson` extension is included for outbound calls. `common/integration` holds the reference gateways — `BankGateway` (interface, one impl per bank: `HdfcBankGateway`/`IdfcBankGateway`), `BankRouter` (picks the gateway by anchor/bank, discovers impls via CDI), and `LmsGateway`; all in-process mocks in this template. For a new integration:

1. Define a `{X}Client` — `@RegisterRestClient(configKey = "...")` interface in `common/`, HTTP contract only, URL from config per profile.
2. Wrap it in a `{X}Gateway` (`@ApplicationScoped`) in `common/integration/`; modules inject the **gateway**, never the raw client.
3. Call gateways only from outbox processors, **outside** any transaction (§8).

- Every outbound call must have a **timeout** configured — no unbounded REST clients.
- Never trust an upstream response: check status, null-check fields, validate amounts/references before persisting.
- Drive environment differences by config only. Never add `if (dev)` branches in code.

---

## 13. Testing — part of the change, not a follow-up

A change without tests is not done. Tests run against a **throwaway Postgres** (Quarkus Dev Services / Testcontainers — **Docker must be running**) with Flyway migrations applied (mirror `ApplicantResourceTest`). Minimum for any new endpoint or service method:

- **Happy path** integration test through the REST layer.
- **Validation** — invalid/missing fields return 400.
- **State/not-found** — invalid transition returns 409; missing entity returns 404.
- **Money/idempotency** where money moves — a retried operation applies exactly once.

No test asserts only `assertNotNull`. No test is written to match buggy behavior. No `Thread.sleep` for scheduling — drive the processor method directly.

---

## 14. Build and commits

```bash
mvn spotless:apply     # Google Java Format (AOSP) — run before committing
mvn clean verify       # Spotless check + compile + ArchUnit (18 rules) + tests
```

`mvn clean verify` must pass. **Reporting "done" on a red build is a task failure.** Conventional commits (enforced by `.githooks/commit-msg`):

```
feat(applicant): add email validation
# type(scope): description — feat fix docs style refactor perf test build ci chore revert
```

---

## 15. Adding a new module — checklist

1. Package `com.starter.{module}`.
2. `{Module}Api.java` at root — nested records, no annotations.
3. Subpackages `api/`, `api/request/`, `api/response/`, `internal/`, `domain/`.
4. Entity extends `BaseEntity`/`BaseUuidEntity`, `@Entity`, invariants in the public constructor.
5. `{Module}Repo implements PanacheRepository<Entity>`, `@ApplicationScoped`.
6. `{Module}Service implements {Module}Api`, `@ApplicationScoped`, `@Transactional` on writes.
7. `{Module}Resource` at `@Path("/{modules}")`, `@Valid` on every body.
8. Tests per §13.
9. `mvn spotless:apply && mvn clean verify` green.

---

## 16. Anti-slop rules

Each of these is a review rejection:

| Don't | Why |
|---|---|
| Speculative abstractions, generic `<T>` base services, interfaces with one impl | Unowned complexity |
| Copy-pasted near-duplicate services across modules | Divergence bugs |
| `Util`/`Helper`/`Common` catch-all classes | Nothing owns them |
| Comments that restate code, `// TODO`, commented-out code | Noise — delete it |
| Defensive `try/catch` around everything | Hides real failures |
| `Optional` as a field or parameter | Misuse — return type only |
| Reflection, `setAccessible`, `@SuppressWarnings` | Bypasses guardrails |
| Reformatting/renaming files unrelated to the task | Unreviewable diffs |
| Editing `pom.xml`, ArchUnit rules, or CI config unasked | Scope creep |
| A new README/doc per change | Doc sprawl |
| Deep `if` nesting, methods > ~40 lines, classes > ~300 lines | Unreadable |

**Scope discipline:** change only what the task requires. If you spot an unrelated problem, list it in the handoff — don't fix it.

---

## 17. Definition of done — self-review before handoff

- [ ] Placement matches §3; no cross-module `internal`/`domain`/`api` imports; names follow the table.
- [ ] No in-app auth added (§5); any gateway-provided identity is validated, never trusted blindly.
- [ ] Every `Req` field validated; nothing server-owned accepted from the client.
- [ ] No PII/secrets/payloads in logs; no secrets in code, tests, or config.
- [ ] All SQL parameterized; no interpolation. No widened CORS/TLS/auth.
- [ ] Any new dependency is permissively licensed, maintained, and justified in the handoff.
- [ ] `BigDecimal` + explicit rounding for money; `Instant` + `TIMESTAMPTZ` for time.
- [ ] No remote call inside `@Transactional`; money paths idempotent; state transitions validated.
- [ ] Tests per §13 pass; `mvn spotless:apply && mvn clean verify` green.
- [ ] Diff contains only what the task required; conventional commit message.

---

## 18. Handoff report — always output this

```markdown
### What changed
- <files/classes touched and why, one line each>

### Business rules implemented
- <the rule, and where it came from — ticket, existing code, or ASSUMED>

### Dependencies added
- <name, purpose, license, maintenance status — or "none">

### Assumptions I made
- <each assumption a human must confirm; "none" only if truly none>

### Security-relevant decisions
- <permissions added, PII touched, validation added, data exposed in responses>

### Not covered / risks
- <edge cases, race conditions, missing tests, things I could not verify>

### Verification
- Commands run and result (mvn clean verify: PASS/FAIL)
- What a reviewer should manually test
```

If you could not follow a rule in this document, say which rule and why — explicitly, at the top of the report. Do not hide it.
