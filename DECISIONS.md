# Design Decisions

Why this starter is shaped the way it is. Each entry is a decision, the reason, and the trade-off we
accepted. The rules themselves live in [CLAUDE.md](CLAUDE.md); this is the "why" behind them.

---

### Java 25 (LTS) on Quarkus 3.33 LTS, no workarounds
The original ran Java 25 with hacks (`--enable-preview`, Byte Buddy experimental, `--add-opens`)
because the Quarkus of the day (3.23) didn't support Java 25 cleanly. We briefly retargeted to Java 21
to shed those hacks, then upgraded to **Quarkus 3.33 LTS**, which has **full, native Java 25 support**.
**Why:** Java 25 is the current Java LTS and 3.33 the current Quarkus LTS — an LTS-on-LTS baseline that
picks up the language/perf/security gains with none of the workarounds. **Trade-off:** none meaningful;
the build is clean (no preview flags, no `--add-opens`, standard Byte Buddy). Java 21 is still supported
by 3.33 if a target environment requires it — this is a one-line `pom` change plus `mise.toml`.

### Authentication/authorization at the gateway, none in-app
The in-app JWT + permission machinery was removed; the service sits behind **APISix**, which does
authn/authz. **Why:** one enforcement point, no duplicated/critically-security-sensitive code per
service. **Trade-off:** the app must still validate all input and never trust a gateway-forwarded
identity blindly (documented in CLAUDE.md §5). Re-add in-app auth only if a ticket demands it.

### Schema: Flyway, never Hibernate auto-DDL
`hibernate.database.generation=none`; versioned SQL migrations in `db/migration`, applied on startup.
**Why:** in a regulated NBFC, schema changes must be reviewed and auditable — Hibernate `update`
can't do data migrations, has no rollback, and silently drifts. Flyway over Liquibase because we're
Postgres-only and auditors read plain SQL, not a changelog DSL. **Trade-off:** you hand-write SQL and
never edit a shipped migration (add the next `V*`). Tests use H2 + Hibernate `drop-and-create` (Flyway
off), so migrations are exercised in dev/deploy, not the test suite.

### No Hibernate Envers audit trail (yet)
`@Audited` is deliberately absent. **Why:** it adds a dependency and audit tables not every service
needs on day one. **Trade-off:** if column-level history is required, add `quarkus-hibernate-envers`
and say so in the handoff — don't assume it exists.

### Outbox: remote calls happen *outside* the transaction
`initiate` writes the business row + an `OutboxEvent` in one transaction and makes no remote call. The
scheduled processors then **claim (txn) → call bank/LMS (no txn) → finalise (txn)**. **Why:** the
reference implementation we started from wrapped the whole processor in `@Transactional` and made the
bank call inside it — holding a DB connection across a slow remote call, the exact failure the outbox
exists to prevent. **Trade-off:** more methods/transaction boundaries, but it's correct and idempotent.

### REST: bare success bodies, org-style errors with field detail
Success responses are the bare `Res`/`PageRes` record — **no** `{status, data}` envelope. Errors use
the org guide's `{ status, message, errors[] }` with field-level detail on 400s. **Why:** the envelope
duplicates the HTTP status into JSON and forces every client to unwrap `.data`; the original code
never used it either. Field-level errors were the one genuinely better part of the org guide, so we
adopted that. **Trade-off:** an asymmetry (bare success, enveloped errors) — intentional and documented.

### Pagination is 1-based
`page=1` is the first page (`@Min(1)`, offset `(page-1)*size`); response is `PageRes<T>`. `sort` maps
through a fixed allow-list before touching SQL. **Why:** 1-based reads naturally for humans; the
allow-list prevents ORDER BY injection. **Trade-off:** differs from 0-based JPA/Spring defaults, so
it's stated explicitly.

### API versioning stays in the app (`/api/v1`)
**Why:** the gateway's service `path_prefix` (e.g. `/central`) is a router, not a version — they're
orthogonal, so in-app `/api/v1` isn't redundant. **Trade-off:** versioning could instead be owned by
the gateway; we chose in-app for locality. Revisit if the org standardises versioning at the edge.

### Exception standard
Business/JDK exceptions are thrown from services (never `jakarta.ws.rs`, ArchUnit-enforced) and the
`common/exception` mappers translate them:
- **`NotFoundException` → 404** — a dedicated type, *not* `NoSuchElementException`. **Why:** a raw
  `NoSuchElementException` (empty collection, bad iterator) is a bug and must surface as 500, not be
  laundered into a client 404.
- **`IllegalArgumentException` / `NPE` → unmapped → 500.** **Why:** they mean "the caller violated a
  method contract" — a programming error, not client input. New devs reach for them reflexively;
  leaving them unmapped stops a bug from masquerading as a 4xx.
- **`BusinessValidationException` → 422** for well-formed-but-unprocessable business rules (named to
  avoid the silent clash with `jakarta.validation.ValidationException`).
- **`ConstraintViolationException` → 400** with `errors[]`, from `@Valid` or the injected `Validator`.

### `ValidationExceptionMapper` is a separate exact-type mapper
It targets `ResteasyReactiveViolationException` specifically. **Why:** JAX-RS selects mappers by
nearest-supertype first, so `@Priority` on the `ExceptionMapper<Exception>` can't override Quarkus's
built-in validation mapper — only a same-type user mapper can. **Trade-off:** two mapper classes, but
they share one response builder.

### Field validation: declarative by default, programmatic when needed
Annotations on `Req` + `@Valid` cover ~all field-shape cases (cross-field via class-level
`@AssertTrue`). For rules assembled at runtime or sharing an allow-list, validate in code via the
injected `Validator` — it yields the same 400 `errors[]` shape. **Why:** annotations aren't a 100%
rule; the programmatic escape hatch reuses the same machinery instead of hand-building errors.

### Persistence: Panache + a fluent `QueryRepo`, no JOOQ
CRUD via Panache; native SQL projections via a `QueryRepo` (fluent, `Tuple`-based, named + positional
params). **Why:** kept the richer fluent wrapper over the original's two-method one; JOOQ adds codegen
and build coupling we don't need when we're comfortable with SQL. **Trade-off:** you write SQL by hand
(parameterised — string interpolation into SQL is forbidden).

### Dependencies: allowed, with guardrails
Not restricted to today's `pom.xml`. **Why:** teams (and AI) need real libraries. **Guardrails:**
permissive licenses only (Apache/MIT/BSD/EPL/MPL) — **no** copyleft (GPL/AGPL/LGPL/SSPL), no
unmaintained/CVE-ridden libs, and no Lombok/MapStruct (build-magic that fights the toolchain). Every
addition is justified in the handoff.

### Deployment: Kamal
Docker image + `config/deploy.*.yml` + GitHub workflows, behind APISix. **Why:** matches the team's
existing Kamal-based rollout. DNS/TLS/registry/secrets are owned by DevOps — the committed configs use
placeholders.

### Docs: two for humans, one contract
[CLAUDE.md](CLAUDE.md) is the standards contract (AI + human). [README.md](README.md) is onboarding.
This file is the decision log. **Why:** the earlier `CONTRIBUTION.md`/`MAINTAINER.md`/`SETUP.md` sprawl
duplicated and drifted (they still referenced Java 25); consolidating keeps one source of truth per
audience.
