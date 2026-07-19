# Quarkus Enterprise Starter

A standards-based, secure starter for our Quarkus + Java 25 + PostgreSQL services. Clone it, build
your capability on the reference modules, and ship it behind the API gateway.

- **The rules** (architecture, REST, exceptions, outbox, money/PII) — [CLAUDE.md](CLAUDE.md). Written
  for AI agents, but plain enough to be your engineering handbook too. Read it before contributing.
- **Why it's built this way** — [DECISIONS.md](DECISIONS.md).
- **Reference modules:** `applicant` (CRUD + pagination), `drawdown` (cross-module orchestration),
  `payment` (money + outbox). Mirror the closest one; don't invent new structure.

---

## Prerequisites

| Tool       | Version | Purpose                 |
|------------|---------|-------------------------|
| mise       | latest  | Java version management |
| Maven      | 3.9+    | Build tool              |
| Git        | 2.x     | Version control         |
| Docker     | latest  | Local Postgres + image builds |
| PostgreSQL | 15+     | Database (via Docker for local dev) |

---

## Local setup

**1. Java 25 (via [mise](https://mise.jdx.dev/)):** `mise.toml` pins the version.
```bash
mise install
java -version                  # should show 25.x
# if it shows an older JDK, activate mise in your shell:
eval "$(mise activate bash)"   # or zsh
```

**2. Database — local Postgres via Docker:**
```bash
docker compose up -d           # db: starter, port 5432, user/pass: postgres
```
Schema is created by **Flyway** migrations on startup (first `mvn quarkus:dev`) — no manual SQL.

**3. Credentials** — create `.env` at the project root (gitignored, never commit real values):
```
QUARKUS_DATASOURCE_USERNAME=postgres
QUARKUS_DATASOURCE_PASSWORD=postgres
```

**4. Run / build:**
```bash
mvn quarkus:dev                # dev mode, live reload → http://localhost:8080
mvn spotless:apply             # format before committing
mvn clean verify               # format check + ArchUnit + tests (must pass)
```

Dev endpoints: Swagger UI `/q/swagger-ui` · health `/q/health` · metrics `/q/metrics` · Dev UI `/q/dev-ui`.

> **Tests need Docker.** They run against a throwaway Postgres started by Quarkus Dev Services
> (Testcontainers), and Flyway applies the real migrations. With Docker Desktop it works out of the
> box; on **Colima** set `export DOCKER_HOST="unix://$HOME/.colima/default/docker.sock"`.

---

## Configuration

| File | Contains | Committed |
|---|---|---|
| `application.properties` | shared config | yes |
| `application-dev.properties` | local dev datasource (docker-compose DB) | yes |
| `application-test.properties` | Postgres Dev Services (Testcontainers) + Flyway | yes |
| `application-staging.properties` / `application-production.properties` | profile config (non-secret) | yes |
| `.env` | **credentials only** | **no** |

`.env` env vars have the **highest priority** in Quarkus and can't be overridden by any properties
file — so only harmless, test-safe credentials live there. Staging/production DB URL and credentials
come from the **environment** at deploy time (Kamal `env.clear` / `env.secret`), never a committed
file. Details in [CLAUDE.md §11](CLAUDE.md).

---

## Commits & formatting

`.githooks/commit-msg` enforces **Conventional Commits** (Maven wires `core.hooksPath` on `initialize`):
```bash
git commit -m "feat(applicant): add email validation"   # ✅
git commit -m "added stuff"                              # ❌ rejected
```
Spotless (Google Java Format, AOSP) runs in the `validate` phase and fails the build on unformatted
code — run `mvn spotless:apply` before committing.

---

## Quality & security tooling

Almost all of it is **automatic** — gates that fire at different moments. You rarely run any by hand.

| When | Tool | Catches | You do |
|---|---|---|---|
| **`git commit`** (local) | gitleaks hook · commit-msg hook | committed secrets · bad commit format | nothing unless it blocks you |
| **`mvn verify`** (local + CI) | Spotless · SpotBugs + Find-Sec-Bugs · ArchUnit · tests + JaCoCo | formatting · bugs & security in *your code* · architecture drift · coverage < 85% | fix what goes red |
| **CI** (every PR/push) | *(all of `mvn verify`, plus)* Trivy · gitleaks | dependency CVEs · image/secret/misconfig · secrets in history | bump the flagged dep |
| **Weekly** (scheduled) | Trivy · Renovate | newly-disclosed CVEs · out-of-date dependencies | review & merge Renovate's PRs |

Think of it as: **your code** is guarded by Spotless/ArchUnit/SpotBugs/JaCoCo, **your dependencies** by
Trivy + Renovate, **your secrets** by gitleaks. Nothing overlaps.

**Your whole day-to-day is three things:**

1. Before pushing: `mvn spotless:apply` then `mvn clean verify` (green = all local gates pass).
2. When a gate goes red (local or CI): read the message, fix the one thing it names.
3. Weekly: review and merge the [Renovate](renovate.json) dependency PRs (CI has already vetted them).

Config lives in: `pom.xml` (SpotBugs `spotbugs-exclude.xml`, JaCoCo gate), `.github/workflows/security.yml`
(Trivy + gitleaks), `.gitleaks.toml`, `.github/workflows/renovate.yml` + `renovate.json`. Renovate needs a
one-time Forgejo bot token (`RENOVATE_TOKEN`) — see that workflow's header. Rationale in [DECISIONS.md](DECISIONS.md).

---

## Working with AI agents (Claude Code)

You may use AI to write code — but you own what you hand off:

- **[CLAUDE.md](CLAUDE.md) is the contract.** Claude Code reads it automatically; keep prompts within
  it. If an agent proposes something that contradicts it, the file wins.
- **Mirror the closest reference module** (`applicant`/`drawdown`/`payment`) and `common/*` — don't
  let the agent invent new structure.
- **Review before you commit.** Read the diff, run `mvn clean verify` locally, confirm the handoff
  report (CLAUDE.md §18). "The AI wrote it" is not a review.
- Never let an agent weaken a check (tests, ArchUnit, Spotless) or touch secrets to go green.

---

## Deployment

**Kamal** — image from the root `Dockerfile`, config in `config/deploy.*.yml`, driven by
`.github/workflows/deploy-*.yml`. The service runs **behind the API gateway (APISix)**, which owns
authentication and authorization.

**Coordinate with DevOps** for the actual rollout — DNS, TLS/hosting, registry access, target
servers, and environment secrets are theirs. Don't point the deploy configs at real infrastructure
on your own.
