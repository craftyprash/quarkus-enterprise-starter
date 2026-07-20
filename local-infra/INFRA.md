# Local Observability & Quality — run it, see it

How to run the app against a full observability stack **on your machine**, make API calls, and view
logs, traces, errors, metrics, and code quality. Everything here is free, self-hostable, and **not
tied to any cloud** — the same signals work wherever you deploy (see "Portability" at the bottom).

> This is a **demo/local** stack. It is separate from the app's own `docker-compose.yml` (which only
> runs the app's Postgres). Nothing here is for production.

---

## What's in the stack

| Service | Purpose | URL |
|---|---|---|
| **otel-lgtm** (Grafana + Loki + Tempo + Prometheus + OTel Collector) | logs, traces, metrics — one UI | http://localhost:3000 |
| **GlitchTip** *(profile `errors`)* | error tracking (Sentry-compatible) | http://localhost:8000 |
| **SonarQube** *(profile `sonar`)* | code-quality dashboard | http://localhost:9000 |

The app emits **OTLP** (traces, logs, metrics) to otel-lgtm and **Sentry-protocol** errors to
GlitchTip. It knows nothing else — the backends are just endpoints.

---

## 1. Start the stack

```bash
# core: Grafana + Loki + Tempo + Prometheus (lightweight)
docker compose -f local-infra/compose.yml up -d

# add error tracking
docker compose -f local-infra/compose.yml --profile errors up -d

# add code-quality dashboard  (SonarQube needs: sudo sysctl -w vm.max_map_count=262144
#                              — on Colima: `colima ssh -- sudo sysctl -w vm.max_map_count=262144`)
docker compose -f local-infra/compose.yml --profile sonar up -d
```

## 2. Run the app pointing at it

The app's own DB first, then the app with tracing switched on. Use **environment variables**, not
`-D` — `mvn quarkus:dev` forks the app into a separate JVM, so `-D` flags stay on the Maven JVM and
never reach it. (`QUARKUS_OTEL_LOGS_ENABLED=true` additionally ships logs to Loki over OTLP; without
it, logs still print as JSON to the console.)

```bash
docker compose up -d                       # app Postgres (root compose)

QUARKUS_OTEL_SDK_DISABLED=false \
QUARKUS_OTEL_LOGS_ENABLED=true \
OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:4317 \
  mvn quarkus:dev
```

## 3. Make some API calls

```bash
A=$(curl -s -X POST localhost:8080/api/v1/applicants -H 'Content-Type: application/json' \
      -d '{"name":"Asha","email":"asha@example.com"}' | grep -o '"id":[0-9]*' | cut -d: -f2)
curl -s localhost:8080/api/v1/applicants/$A                 # 200
curl -s "localhost:8080/api/v1/applicants?page=1&size=10"   # 200 (paginated)
curl -s localhost:8080/api/v1/applicants/99999              # 404
```

Each response carries an **`X-Request-Id`** header — that same id is in every log line and trace for
the request.

## 4. View each signal

- **Traces** → Grafana (http://localhost:3000) → **Explore → Tempo → Search**. Click a trace to see
  the request span, the DB spans, timing.
- **Logs** → Grafana → **Explore → Loki** (`{service_name="quarkus-starter-enterprise"}`). Log lines
  carry `requestId`, `traceId`, `spanId`; from a trace you can jump straight to its logs.
- **Metrics** → Grafana → **Explore → Prometheus**, or the raw feed at http://localhost:8080/q/metrics.
- **Errors** → GlitchTip (needs the `errors` profile): see step 5.
- **Health** → http://localhost:8080/q/health.

## 5. Errors in GlitchTip

1. Open http://localhost:8000, register (the first user is the admin), create an **Organization** and
   a **Project** (platform: *Java*). Copy the project's **DSN** (looks like
   `http://<key>@localhost:8000/1`).
2. Restart the app with the DSN set (errors are sent at `ERROR` level):
   ```bash
   SENTRY_DSN="http://<key>@localhost:8000/1" QUARKUS_OTEL_SDK_DISABLED=false \
   OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:4317 \
     mvn quarkus:dev
   ```
3. Trigger a **500** (unhandled error) — the easy way is to stop the DB and hit an endpoint:
   ```bash
   docker compose stop postgres
   curl -s localhost:8080/api/v1/applicants/1     # DB down -> 500 -> captured
   docker compose start postgres
   ```
   The exception now shows in GlitchTip — grouped, with stack trace and frequency. That grouping /
   dedup / alerting is why a dedicated error tracker beats grepping logs.

## 6. Code quality in SonarQube

With the `sonar` profile up, generate a token in the UI (http://localhost:9000, admin/admin), then:
```bash
mvn verify sonar:sonar -Dsonar.host.url=http://localhost:9000 -Dsonar.token=<token> \
  -Dsonar.coverage.jacoco.xmlReportPaths=target/jacoco-report/jacoco.xml
```

## Teardown

```bash
docker compose -f local-infra/compose.yml --profile errors --profile sonar down     # add -v to wipe data
```

---

## Portability — why none of this is baked into the app

The app is coupled to **no specific backend**. It emits three standard things:

| Signal | How the app emits it | Swap the backend by… |
|---|---|---|
| **Logs** | JSON to **stdout** | your log shipper (Fluent Bit, etc.) → CloudWatch / OpenSearch / Loki / S3 |
| **Traces** | **OTLP** to `OTEL_EXPORTER_OTLP_ENDPOINT` | pointing at any OTel Collector → Jaeger / Tempo / X-Ray |
| **Errors** | **Sentry protocol** to `SENTRY_DSN` | Sentry SaaS or self-hosted GlitchTip |

So **moving to another infra changes environment variables, not code.** In your AWS setup, Fluent Bit
already ships stdout → CloudWatch/OpenSearch (no app change); point `OTEL_EXPORTER_OTLP_ENDPOINT` at
your collector and `SENTRY_DSN` at your GlitchTip, and the same signals light up.

### Deploying to a test environment
Set these where the container runs (Kamal `env.clear` / `env.secret`, already stubbed in
`config/deploy.*.yml`):
- `OTEL_EXPORTER_OTLP_ENDPOINT` → your collector
- `SENTRY_DSN` → your GlitchTip project DSN (leave unset to disable)
- staging/production already enable JSON logs + tracing (`application-{staging,production}.properties`)

Then make API calls through APISix and view them exactly as above, against your real backends.
