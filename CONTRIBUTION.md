# Engineering Contribution Guide

This document defines **how we write code** in this repository. Every example here is from the actual codebase — follow the patterns exactly.

---

# Module Structure

Each business capability is a **module** — a top-level package under `com.starter`.

```
com.starter.payment/
  PaymentApi.java                ← contract interface (the only public face)
  api/
    PaymentResource.java         ← REST endpoints
    request/
    response/
      PaymentRes.java            ← HTTP output (JSON shape)
  internal/
    PaymentService.java          ← business logic, implements PaymentApi
    PaymentRepo.java             ← Panache repository (CRUD)
    OutboxRepo.java              ← Panache repository for outbox events
    DisbursementProcessor.java   ← outbox processor (scheduled)
    NeftSettlementPoller.java    ← polls bank for NEFT settlement
  domain/
    Payment.java                 ← JPA entity
    OutboxEvent.java             ← outbox event entity (no @Audited — infrastructure, not business data)
```

External integrations (bank, LMS) live in `common/integration/` — shared across modules:

```
com.starter.common/
  integration/
    bank/
      BankGateway.java           ← strategy interface
      BankRouter.java            ← selects gateway by anchor/bank code
      IdfcBankClient.java        ← @RegisterRestClient for IDFC API
      IdfcBankGateway.java       ← wraps IdfcBankClient
      HdfcBankClient.java        ← @RegisterRestClient for HDFC API
      HdfcBankGateway.java       ← wraps HdfcBankClient
    lms/
      LmsClient.java             ← @RegisterRestClient for LMS API
      LmsGateway.java            ← wraps LmsClient
```

Every module follows this layout. No exceptions.

---

# Package Responsibilities

| Package | Contains | Does NOT contain |
|---|---|---|
| Module root | Contract interface only | Concrete classes, services, entities |
| `api` | REST resource, request/response records | Business logic, repository calls, transactions |
| `internal` | Service, repositories, processors | REST annotations, HTTP concerns, integration clients |
| `domain` | JPA entities, domain invariants | CDI, REST, remote calls |
| `common/integration` | REST clients, gateway wrappers, routers | Business logic, entities |

---

# Module Contract

Every module exposes a **single interface** at its package root. This is the **only** way other modules interact with yours.

```java
package com.starter.payment;

public interface PaymentApi {

    record Info(
            Long id,
            Long drawdownId,
            String bank,
            String transferMode,
            BigDecimal amount,
            String status,
            String bankReference,
            Instant createdAt) {}

    record InitiateInput(Long drawdownId, String anchorCode, BigDecimal amount) {}

    Info initiate(InitiateInput input);

    Info findById(Long id);
}
```

Rules:
- Lives at the **module root** — `com.starter.payment.PaymentApi`
- Input/output types are **nested records** inside the interface
- Contract types carry **no annotations** — no JSON, no validation, pure data
- The service in `internal/` implements this interface

When another module needs your capability:

```java
package com.starter.drawdown.internal;

@ApplicationScoped
public class DrawdownService implements DrawdownApi {

    @Inject ApplicantApi applicantApi;   // ← interface, never the concrete service
    @Inject PaymentApi paymentApi;       // ← same pattern

    @Override
    @Transactional
    public Info disburse(Long id) {
        var drawdown = repo.findByIdOptional(id)
                .orElseThrow(() -> new NoSuchElementException("Drawdown not found"));

        if (!"PENDING".equals(drawdown.status)) {
            throw new IllegalStateException("Drawdown not in PENDING status");
        }

        drawdown.status = "DISBURSING";

        paymentApi.initiate(
                new PaymentApi.InitiateInput(
                        drawdown.id, drawdown.anchorCode, drawdown.amount));

        var applicant = applicantApi.findById(drawdown.applicantId);
        return toInfo(drawdown, applicant.name());
    }
}
```

### What's allowed across modules

| ✅ Allowed | ❌ Not allowed |
|---|---|
| `drawdown.internal` → `PaymentApi` | `drawdown.internal` → `payment.internal.*` |
| `drawdown.internal` → `PaymentApi.InitiateInput` | `drawdown.internal` → `payment.domain.*` |
| | `drawdown.internal` → `payment.api.*` |

The build enforces this — you'll get an ArchUnit failure if you reach into another module's internals.

---

# REST Layer

The resource maps between HTTP types and contract types. It owns the HTTP shape — status codes, JSON structure, validation.

```java
package com.starter.drawdown.api;

@Path("/drawdowns")
public class DrawdownResource {

    @Inject DrawdownService service;

    @POST
    public Response create(@Valid CreateDrawdownReq req) {
        var info = service.create(
                new DrawdownApi.CreateInput(req.applicantId(), req.anchorCode(), req.amount()));
        return Response.status(201).entity(toRes(info)).build();
    }

    @POST
    @Path("/{id}/disburse")
    public DrawdownRes disburse(@PathParam("id") Long id) {
        return toRes(service.disburse(id));
    }

    @GET
    @Path("/{id}")
    public DrawdownRes findById(@PathParam("id") Long id) {
        return toRes(service.findById(id));
    }

    private DrawdownRes toRes(DrawdownApi.Info info) {
        return new DrawdownRes(
                info.id(), info.applicantId(), info.applicantName(),
                info.anchorCode(), info.amount(), info.status(), info.createdAt());
    }
}
```

### HTTP Request — validated, annotated

```java
package com.starter.drawdown.api.request;

public record CreateDrawdownReq(
        @NotNull Long applicantId,
        @NotBlank String anchorCode,
        @NotNull @Positive BigDecimal amount) {}
```

### HTTP Response — JSON shape for the client

```java
package com.starter.drawdown.api.response;

public record DrawdownRes(
        Long id, Long applicantId, String applicantName,
        String anchorCode, BigDecimal amount, String status, Instant createdAt) {}
```

Why separate HTTP types from contract types? The contract is stable across modules. The HTTP shape can evolve independently — add `@JsonProperty`, `@Schema`, pagination wrappers — without touching the contract.

---

# Entity and Base Classes

Every entity **must** extend one of the two base classes:

```java
package com.starter.payment.domain;

@Entity
@Audited
public class Payment extends BaseEntity {

    @Column(name = "drawdown_id", nullable = false)
    public Long drawdownId;

    @Column(nullable = false)
    public String bank;

    @Column(name = "transfer_mode", nullable = false)
    public String transferMode;

    @Column(nullable = false, precision = 15, scale = 2)
    public BigDecimal amount;

    @Column(nullable = false)
    public String status = "INITIATED";

    @Column(name = "bank_reference")
    public String bankReference;

    protected Payment() {}                                                       // JPA requires it

    public Payment(Long drawdownId, String bank, String transferMode, BigDecimal amount) {
        this.drawdownId = Objects.requireNonNull(drawdownId, "drawdownId required");
        this.bank = Objects.requireNonNull(bank, "bank required");
        this.transferMode = Objects.requireNonNull(transferMode, "transferMode required");
        this.amount = Objects.requireNonNull(amount, "amount required");
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

- `@Entity` and `@Audited` (Hibernate Envers) on every business entity
- Infrastructure entities like `OutboxEvent` do **not** get `@Audited` — they're operational, not auditable business data
- Protected no-arg constructor for JPA
- Public constructor enforces domain invariants with `Objects.requireNonNull`
- Public fields (Panache style) — no getters/setters
- Lives in `domain/` package only
- References other modules by **ID** (`Long drawdownId`), never by entity reference

### Domain dependencies

| ✅ Allowed | ❌ Not allowed |
|---|---|
| `payment.domain` → `common.domain` (BaseEntity, BaseUuidEntity) | `payment.domain` → `drawdown.domain` |
| `payment.domain` → JDK / Jakarta Persistence | `payment.domain` → any other module's domain |

`common.domain` is shared infrastructure — base classes, shared value types. Another module's domain is its private data model. If you need data from another module, go through the contract interface in `internal/`, never import their entity.

---

# Naming Conventions

| What | Suffix | Location | Example |
|---|---|---|---|
| Module contract | `Api` | Module root | `PaymentApi` |
| REST endpoint | `Resource` | `api/` | `PaymentResource` |
| HTTP input | `Req` | `api/request/` | `CreateDrawdownReq` |
| HTTP output | `Res` | `api/response/` | `PaymentRes` |
| Panache repository | `Repo` | `internal/` | `PaymentRepo` |
| Native SQL repository | `QueryRepo` | `internal/` | `ApplicantQueryRepo` |
| Business logic | `Service` | `internal/` | `PaymentService` |
| JPA entity | (no suffix) | `domain/` | `Payment` |
| Strategy interface | `Gateway` | `common/integration/` | `BankGateway` |
| Strategy router | `Router` | `common/integration/` | `BankRouter` |
| REST client | `Client` | `common/integration/` | `IdfcBankClient` |
| Scheduled processor | `Processor` / `Poller` | `internal/` | `DisbursementProcessor` |

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
package com.starter.payment.internal;

@ApplicationScoped
public class PaymentRepo implements PanacheRepository<Payment> {

    public List<Payment> findByStatus(String status) {
        return find("status", status).list();
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
private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

log.info("Payment initiated id={} bank={} mode={}", payment.id, payment.bank, payment.transferMode);
```

- `info` for state changes (created, disbursed, settled, failed)
- `debug` for diagnostics
- Never log sensitive data or full request payloads

---

# Exceptions

Services must **not** depend on `jakarta.ws.rs` — this is enforced by ArchUnit. Throw JDK exceptions or the one custom exception in `common.exception`.

| Exception | When to use | HTTP status (mapped by GlobalExceptionMapper) |
|---|---|---|
| `NoSuchElementException` | Entity not found | 404 |
| `IllegalArgumentException` | Bad input, invalid config | 422 |
| `IllegalStateException` | Wrong state for operation | 409 |
| `DuplicateException` | Uniqueness violation | 409 |
| `ForbiddenException` | Missing permission or data scope | 403 |
| `ConstraintViolationException` | Jakarta validation failure | 400 |

```java
// ✅ service throws pure Java
var drawdown = repo.findByIdOptional(id)
        .orElseThrow(() -> new NoSuchElementException("Drawdown not found"));

if (!"PENDING".equals(drawdown.status)) {
    throw new IllegalStateException("Drawdown not in PENDING status");
}

repo.findByEmail(req.email())
        .ifPresent(a -> { throw new DuplicateException("Email already exists"); });
```

The `GlobalExceptionMapper` maps these to a standard error response:

```json
{"status": 404, "error": "NOT_FOUND", "message": "Drawdown not found"}
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
    "drawdown": ["view", "create", "disburse"],
    "payment": ["view"]
  },
  "scopes": {
    "branch": ["BR001", "BR002"]
  }
}
```

This is returned by the external permission service and cached per user token.

### Endpoint-level — `@RequiresPermission`

Declarative check on REST resource methods:

```java
@GET
@RequiresPermission(resource = "drawdown", action = "view")
public DrawdownRes findById(@PathParam("id") Long id) {
    return toRes(service.findById(id));
}

@POST
@Path("/{id}/disburse")
@RequiresPermission(resource = "drawdown", action = "disburse")
public DrawdownRes disburse(@PathParam("id") Long id) {
    return toRes(service.disburse(id));
}
```

If the user lacks the permission, the interceptor throws `ForbiddenException` → 403.

### Service-level — record checks

For business rules that depend on the specific record (branch access, ownership), inject `PermissionContext` in the service:

```java
@Inject PermissionContext permissionContext;

public Info findById(Long id) {
    var drawdown = repo.findByIdOptional(id)
            .orElseThrow(() -> new NoSuchElementException("Drawdown not found"));

    if (!permissionContext.scope("branch").contains(drawdown.branchCode)) {
        throw new ForbiddenException("No access to this branch");
    }

    return toInfo(drawdown);
}
```

### PermissionContext API

```java
context.has("drawdown", "view")              // exact check
context.hasAny("drawdown", "view", "update") // any of these actions
context.scope("branch")                      // returns Set<String> of allowed values
context.userId()                             // current user ID from JWT subject
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

All authorization infrastructure lives in `common` — it's cross-cutting, not a business module.

---

# Response Conventions

- `POST` → `Response` (for 201 status + body)
- `GET`, `PUT`, `DELETE` → return the typed record directly (200 implicit)
- Error shape → standard `ErrorRes` from `GlobalExceptionMapper`

```java
@POST
public Response create(@Valid CreateDrawdownReq req) {
    var info = service.create(...);
    return Response.status(201).entity(toRes(info)).build();
}

@GET
@Path("/{id}")
public DrawdownRes findById(@PathParam("id") Long id) {
    return toRes(service.findById(id));   // 200 implicit, exception → mapper handles error shape
}
```

---

# Transaction Rules

Transactions live **only** in `internal/` services.

```java
@Transactional
public Info initiate(InitiateInput input) {
    // ✅ DB reads, writes, entity operations
    paymentRepo.persist(payment);
    outboxRepo.persist(event);
}
```

### Never call external APIs inside a transaction

A transaction holds a database connection. If you call an external API (HTTP, messaging) inside it, a slow or failed remote call holds that connection open — potentially exhausting the pool.

```java
// ❌ WRONG — remote call inside transaction
@Transactional
public void disburse(Long drawdownId) {
    repo.persist(payment);
    bankGateway.disburse(payment.id, payment.amount);   // holds DB connection while waiting for bank
    lmsGateway.recordDisbursement(drawdownId, amount);   // and again for LMS
}
```

This is where the **outbox pattern** comes in.

---

# Outbox Pattern — No Remote Calls in Transactions

When a business operation needs to trigger external systems (bank disbursement, LMS entry, notifications), use the outbox pattern: **persist an outbox record in the same transaction**, then process it after commit via a scheduled poller.

## The Full Payment Disbursement Flow

This is a real flow in the codebase. When a drawdown is marked for disbursement:

1. **Drawdown module** updates status to `DISBURSING` and calls `PaymentApi.initiate()`
2. **Payment module** creates a `Payment` + `OutboxEvent` in one transaction (no remote calls)
3. **DisbursementProcessor** (scheduled every 5s) picks up the outbox event, calls the bank
4. **IMPS** → bank confirms instantly → update LMS immediately
5. **NEFT** → bank returns a reference → payment enters `POLLING` state
6. **NeftSettlementPoller** (scheduled every 30s) checks bank status → on settlement, updates LMS

### The outbox entity

```java
package com.starter.payment.domain;

@Entity
@Table(name = "outbox_event")
public class OutboxEvent extends BaseEntity {

    @Column(name = "aggregate_type", nullable = false)
    public String aggregateType;     // "PAYMENT"

    @Column(name = "aggregate_id", nullable = false)
    public Long aggregateId;         // payment ID

    @Column(name = "event_type", nullable = false)
    public String eventType;         // "DISBURSE_REQUESTED"

    @Column(columnDefinition = "TEXT")
    public String payload;

    @Column(nullable = false)
    public String status = "PENDING";

    protected OutboxEvent() {}

    public OutboxEvent(String aggregateType, Long aggregateId, String eventType, String payload) {
        this.aggregateType = Objects.requireNonNull(aggregateType, "aggregateType required");
        this.aggregateId = Objects.requireNonNull(aggregateId, "aggregateId required");
        this.eventType = Objects.requireNonNull(eventType, "eventType required");
        this.payload = payload;
    }
}
```

### Step 1 — Service writes payment + outbox in one transaction

```java
package com.starter.payment.internal;

@ApplicationScoped
public class PaymentService implements PaymentApi {

    @Inject PaymentRepo paymentRepo;
    @Inject OutboxRepo outboxRepo;
    @Inject BankRouter bankRouter;

    @Override
    @Transactional
    public Info initiate(InitiateInput input) {
        var gateway = bankRouter.resolveByAnchor(input.anchorCode());
        var transferMode = gateway.transferMode(input.anchorCode());

        var payment = new Payment(input.drawdownId(), gateway.bankCode(), transferMode, input.amount());
        paymentRepo.persist(payment);

        // outbox event — same transaction, guaranteed to commit together
        var event = new OutboxEvent(
                "PAYMENT", payment.id, "DISBURSE_REQUESTED",
                """
                {"paymentId": %d, "drawdownId": %d, "amount": "%s"}
                """.formatted(payment.id, input.drawdownId(), input.amount()));
        outboxRepo.persist(event);

        log.info("Payment initiated id={} bank={} mode={}", payment.id, payment.bank, transferMode);
        // ✅ no remote call here — transaction commits cleanly
        return toInfo(payment);
    }
}
```

### Step 2 — Bank selection via strategy pattern

Multiple banks are supported. Integrations live in `common/integration/bank/` — shared across modules. Each bank has a `@RegisterRestClient` interface (the HTTP contract) and a `BankGateway` implementation (the wrapper).

```java
package com.starter.common.integration.bank;

public interface BankGateway {

    String bankCode();

    String transferMode(String anchorCode);

    String disburse(Long paymentId, BigDecimal amount);

    String checkStatus(String bankReference);
}
```

```java
@RegisterRestClient(configKey = "idfc-bank-api")
@Path("/api/v1")
public interface IdfcBankClient {

    record DisburseRequest(Long paymentId, BigDecimal amount) {}
    record DisburseResponse(String bankReference, String status) {}
    record StatusResponse(String bankReference, String status) {}

    @POST @Path("/disburse")
    DisburseResponse disburse(DisburseRequest request);

    @GET @Path("/status")
    StatusResponse checkStatus(@QueryParam("ref") String bankReference);
}
```

```java
@ApplicationScoped
public class IdfcBankGateway implements BankGateway {

    @Inject @RestClient IdfcBankClient client;

    @Override
    public String bankCode() { return "IDFC"; }

    @Override
    public String transferMode(String anchorCode) { return "IMPS"; }

    @Override
    public String disburse(Long paymentId, BigDecimal amount) {
        var response = client.disburse(new IdfcBankClient.DisburseRequest(paymentId, amount));
        return response.bankReference();
    }

    @Override
    public String checkStatus(String bankReference) {
        return client.checkStatus(bankReference).status();
    }
}
```

The router collects all `BankGateway` implementations via CDI and maps anchor codes to banks:

```java
@ApplicationScoped
public class BankRouter {

    private final Map<String, BankGateway> gatewaysByCode;

    private static final Map<String, String> ANCHOR_BANK_MAP =
            Map.of("TATA", "IDFC", "RELIANCE", "IDFC", "INFOSYS", "HDFC", "WIPRO", "HDFC");

    @Inject
    public BankRouter(Instance<BankGateway> gateways) {
        this.gatewaysByCode = gateways.stream()
                .collect(Collectors.toMap(BankGateway::bankCode, g -> g));
    }

    public BankGateway resolveByAnchor(String anchorCode) { ... }
    public BankGateway resolveByBank(String bankCode) { ... }
}
```

The same pattern applies to HDFC (`HdfcBankClient` + `HdfcBankGateway`) and LMS (`LmsClient` + `LmsGateway`).

To mock any external API, just switch the REST client URL per profile — no code changes needed.

### Step 3 — Outbox processor calls the bank after commit

The `DisbursementProcessor` runs every 5 seconds, picks up `DISBURSE_REQUESTED` events, and calls the bank via `common.integration`. The key branching: **IMPS is instant** (update LMS immediately), **NEFT is async** (enter polling state).

```java
package com.starter.payment.internal;

import com.starter.common.integration.bank.BankRouter;
import com.starter.common.integration.lms.LmsGateway;

@ApplicationScoped
public class DisbursementProcessor {

    @Inject OutboxRepo outboxRepo;
    @Inject PaymentRepo paymentRepo;
    @Inject BankRouter bankRouter;     // from common.integration.bank
    @Inject LmsGateway lmsGateway;     // from common.integration.lms

    @Scheduled(every = "5s", identity = "disbursement-processor")
    @Transactional
    public void processOutbox() {
        var pending = outboxRepo.findPending("DISBURSE_REQUESTED");

        for (var event : pending) {
            var payment = paymentRepo.findByIdOptional(event.aggregateId).orElse(null);
            if (payment == null) {
                event.status = "SKIPPED";
                continue;
            }

            try {
                var gateway = bankRouter.resolveByBank(payment.bank);
                var bankRef = gateway.disburse(payment.id, payment.amount);
                payment.bankReference = bankRef;

                if ("IMPS".equals(payment.transferMode)) {
                    payment.status = "DISBURSED";
                    lmsGateway.recordDisbursement(payment.drawdownId, payment.amount, bankRef);
                } else {
                    payment.status = "POLLING";
                }

                event.status = "PROCESSED";
            } catch (Exception e) {
                event.status = "FAILED";
                payment.status = "FAILED";
                log.error("Disbursement failed payment={}", payment.id, e);
            }
        }
    }
}
```

### Step 4 — NEFT settlement poller

For NEFT payments, settlement isn't instant. The `NeftSettlementPoller` checks the bank periodically and updates LMS once settled.

```java
package com.starter.payment.internal;

import com.starter.common.integration.bank.BankRouter;
import com.starter.common.integration.lms.LmsGateway;

@ApplicationScoped
public class NeftSettlementPoller {

    @Inject PaymentRepo paymentRepo;
    @Inject BankRouter bankRouter;     // from common.integration.bank
    @Inject LmsGateway lmsGateway;     // from common.integration.lms

    @Scheduled(every = "30s", identity = "neft-settlement-poller")
    @Transactional
    public void pollSettlements() {
        var polling = paymentRepo.findByStatus("POLLING");

        for (var payment : polling) {
            try {
                var gateway = bankRouter.resolveByBank(payment.bank);
                var bankStatus = gateway.checkStatus(payment.bankReference);

                switch (bankStatus) {
                    case "SETTLED" -> {
                        payment.status = "DISBURSED";
                        lmsGateway.recordDisbursement(
                                payment.drawdownId, payment.amount, payment.bankReference);
                    }
                    case "FAILED" -> payment.status = "FAILED";
                    default -> log.debug("NEFT still pending payment={}", payment.id);
                }
            } catch (Exception e) {
                log.error("Settlement poll failed payment={}", payment.id, e);
            }
        }
    }
}
```

### Payment status lifecycle

```
INITIATED → DISBURSED (IMPS — instant)
INITIATED → POLLING → DISBURSED (NEFT — async settlement)
INITIATED → FAILED (bank call failed)
POLLING → FAILED (bank reported failure)
```

### Why this matters

| Without outbox | With outbox |
|---|---|
| Remote call fails → transaction rolls back → data lost | Transaction always commits → event retried later |
| Slow API → DB connection held → pool exhaustion | Transaction is fast — only DB writes |
| Partial failure → inconsistent state | Atomic: entity + event commit together |

The outbox record is your **guarantee** that the side-effect will eventually happen, even if the external system is temporarily down.

---

# Database Migrations

Liquibase with **SQL changesets** in `db/changelog/`. Use `db.sh` for all migration operations.

### Creating a new changeset

```bash
# New table with audit table
./db.sh new create-settlement --audit

# Non-table changeset (index, alter, data fix)
./db.sh new add-payment-status-index
```

The `new` command:
- Auto-numbers the file (`004-create-settlement.sql`)
- Generates `CREATE TABLE` boilerplate for `create-*` names (with `TIMESTAMPTZ`, `BIGSERIAL`)
- Adds `_AUD` table boilerplate when `--audit` is passed
- Generates a blank changeset for non-`create-*` names
- Auto-registers in `db.changelog-master.yaml`

### Changeset conventions

```sql
--liquibase formatted sql

--changeset starter:003-create-payment
CREATE TABLE payment (
    id              BIGSERIAL PRIMARY KEY,
    drawdown_id     BIGINT         NOT NULL REFERENCES drawdown(id),
    bank            VARCHAR(50)    NOT NULL,
    transfer_mode   VARCHAR(20)    NOT NULL,
    amount          NUMERIC(15,2)  NOT NULL,
    status          VARCHAR(50)    NOT NULL DEFAULT 'INITIATED',
    bank_reference  VARCHAR(255),
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ    NOT NULL DEFAULT now()
);

--changeset starter:003-create-outbox-event
CREATE TABLE outbox_event (
    id              BIGSERIAL PRIMARY KEY,
    aggregate_type  VARCHAR(100)   NOT NULL,
    aggregate_id    BIGINT         NOT NULL,
    event_type      VARCHAR(100)   NOT NULL,
    payload         TEXT,
    status          VARCHAR(50)    NOT NULL DEFAULT 'PENDING',
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ    NOT NULL DEFAULT now()
);

CREATE INDEX idx_outbox_event_pending ON outbox_event (status, event_type)
    WHERE status = 'PENDING';
```

- File naming: `NNN-short-description.sql`
- Always use `TIMESTAMPTZ` for timestamps
- Always include `created_at` and `updated_at`

### Other db.sh commands

```bash
./db.sh status       # pending changesets
./db.sh validate     # syntax check
./db.sh rollback 1   # rollback last N changesets
./db.sh history      # applied changesets
./db.sh psql         # open psql shell
```

---

# Git Commits

Conventional commits enforced by git hook:

```
type(scope): description
```

Types: `feat` `fix` `docs` `style` `refactor` `perf` `test` `build` `ci` `chore` `revert`

```bash
git commit -m "feat(payment): add disbursement outbox"
```

---

# Integration Layer

External API integrations (bank, LMS, credit bureau, etc.) live in `common/integration/` — **not** inside a business module's `internal/` package.

### Why

- Multiple modules may need the same integration (payment, refund, reconciliation all call bank APIs)
- ArchUnit blocks cross-module `internal` access — integrations trapped in one module can't be reused
- Mocking strategy is simple — switch the REST client URL per profile, no code changes

### Pattern

Each integration has two classes:

1. **Client** — `@RegisterRestClient` interface defining the HTTP contract
2. **Gateway** — `@ApplicationScoped` wrapper that calls the client and maps responses

```java
// Client — the HTTP contract
@RegisterRestClient(configKey = "lms-api")
@Path("/api/v1")
public interface LmsClient {

    record DisbursementRecord(Long drawdownId, BigDecimal amount, String bankReference) {}
    record DisbursementResponse(String status) {}

    @POST @Path("/disbursements")
    DisbursementResponse recordDisbursement(DisbursementRecord request);
}

// Gateway — the wrapper
@ApplicationScoped
public class LmsGateway {

    @Inject @RestClient LmsClient client;

    public void recordDisbursement(Long drawdownId, BigDecimal amount, String bankReference) {
        client.recordDisbursement(new LmsClient.DisbursementRecord(drawdownId, amount, bankReference));
    }
}
```

Modules inject the gateway, never the client directly:

```java
@Inject LmsGateway lmsGateway;     // ✅
@Inject LmsClient lmsClient;       // ❌ use the gateway
```

### Mocking with Microcks

OpenAPI specs for each external API live in `src/main/resources/microcks/`. Import them into [Microcks](https://microcks.io/) to get mock endpoints.

`application-dev.properties` points all REST clients to the Microcks instance:

```properties
quarkus.rest-client.permission-api.url=http://localhost:8585/rest/Permission+API/0.1.0
quarkus.rest-client.idfc-bank-api.url=http://localhost:8585/rest/IDFC+Bank+API/0.1.0
quarkus.rest-client.hdfc-bank-api.url=http://localhost:8585/rest/HDFC+Bank+API/0.1.0
quarkus.rest-client.lms-api.url=http://localhost:8585/rest/LMS+API/0.1.0
```

In production, these point to real service URLs. In tests, they point to `localhost:0` (disabled).

---

# Adding a New Module

Use this checklist. The `payment` module follows this exactly.

1. Create package `com.starter.{module}`
2. Add contract interface: `{Module}Api.java` with nested records
3. Create subpackages: `api/`, `api/request/`, `api/response/`, `internal/`, `domain/`
4. Entity extends `BaseEntity` or `BaseUuidEntity`, annotated with `@Entity` and `@Audited`
5. Repo implements `PanacheRepository<Entity>`, annotated with `@ApplicationScoped`
6. Service implements `{Module}Api`, annotated with `@ApplicationScoped`
7. Resource at `@Path("/{modules}")` in `api/`, maps contract ↔ HTTP types
8. Add Liquibase SQL changeset in `db/changelog/`
9. Run `mvn clean verify` — ArchUnit catches any violations

---

# Quick Reference

If a class **starts a transaction, orchestrates workflows, calls integrations, or implements a module contract** — it belongs in `internal/`.

If a class **defines the HTTP shape** — it belongs in `api/`.

If a class **is the data** — it belongs in `domain/`.

If a class **is the boundary** — it's the interface at the module root.
