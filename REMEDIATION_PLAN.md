# Codebase Remediation Plan — By the Book

> This document **marks** what should change for correctness, security, and
> maintainability. It does **not** change anything. Every finding is grounded in
> the reference library (legend below). Work is phased by severity and risk so
> maintainability improves without regressions.
>
> **Provenance & honesty note.** A full 11-agent parallel audit was launched
> (one auditor per domain, each grounded in its book). The account hit its
> session usage limit and the agents terminated before returning their line-by-
> line findings. This plan is therefore assembled from **direct inspection +
> deep working knowledge of the codebase gathered across this session**, backed
> by targeted detection scans. Sections marked _(needs deep per-file pass)_
> should be re-run through the per-domain auditors once budget resets — the
> structure, book grounding, and phase ordering here are ready to receive them.

## How to read a finding

`path:line | SEVERITY | category | issue | book ref | suggested change`

- **CRITICAL** — correctness/security/data-loss. Fix first, under a green test.
- **HIGH** — real bug risk or major maintainability liability.
- **MEDIUM** — best-practice violation, low risk.
- **LOW** — style/polish; batch opportunistically.

Rule for applying (Khorikov, GOOS): no change lands without a behaviour test
that would fail before and pass after. Refactors land under an already-green
suite, one behaviour-preserving step at a time.

## Grounding legend (the books)

| Area | Book | Used for |
|------|------|----------|
| Java | Bloch — *Effective Java 3rd ed* | immutability, enums, Optional, exceptions, validation, equals/hashCode |
| Go | Harsanyi — *100 Go Mistakes* | goroutine/context/error/concurrency mistakes |
| C | Seacord — *Secure Coding in C and C++ 2nd ed* | memory safety on untrusted wire input |
| Testing | Khorikov — *Unit Testing*; Freeman/Pryce — *GOOS* | behaviour vs implementation, fakes over mocks |
| Observability | Brazil — *Prometheus Up & Running*; Majors — *Observability Engineering* | metric/label/trace design |
| Security | Richer/Sansò — *OAuth 2 in Action*; Seacord | JWT/refresh/token flows |
| Microservices | Newman — *Building Microservices 2nd ed*; Nygard — *Release It 2nd ed* | boundaries, resilience, stability |
| Data | Kleppmann — *Designing Data-Intensive Applications* | persistence, consistency |
| Frontend/UX | Wathan/Schoger — *Refactoring UI*; Yablonski — *Laws of UX* | hierarchy, UX laws, a11y |
| Telecom | 3GPP / O-RAN / TM Forum specs | domain conformance |

---

## Executive summary — the shape of the work

The codebase is in **genuinely good health** after this session's work (CI green
across six languages, no swallowed exceptions in Java, no bare `except` in
Python, only 7 `any`/`ts-ignore` in the whole frontend, no `TODO/FIXME` debt
markers). The remediation is therefore **not** firefighting — it is disciplined
maintainability work. Two themes dominate:

1. **Oversized modules/components** — a handful of Python service modules and
   frontend pages are 700–2800 lines and carry many responsibilities. This is
   the single biggest maintainability liability (SRP; EJ Item 15 minimise
   scope; Refactoring UI's composition ethos).
2. **Missing seams between services and in the build** — three live bugs this
   session (batch dropped the metric `band`; two Dockerfiles wouldn't build/run)
   all escaped CI because there are **no cross-service contract tests and CI
   never builds/runs the container images** (Newman: consumer-driven contracts;
   Nygard: test what you deploy).

Everything else is localised polish.

---

# Findings by area

## 1. Java — common + api-gateway  _(needs deep per-file pass)_

Verified inline:

- `common/constants/*`, `common/security/*` — well-formed non-instantiable
  constant holders (EJ Item 4), `InternalAuthFilter` uses timing-safe
  `MessageDigest.isEqual` (good, OAuth2iA / Seacord constant-time compare). No
  action beyond the items below.
- `api-gateway` `SecurityHeadersFilter`, `JwtAuthenticationFilter`,
  `GlobalExceptionHandler` — reactive `Mono` chains. **MEDIUM** | reactive |
  audit for blocking calls on the event loop and for `onErrorResume` coverage on
  every external call (Nygard: fail fast, no unbounded waits) | confirm each
  `WebClient`/filter path has a timeout and error fallback.
- **MEDIUM** | api-gateway | `JwtAuthenticationFilter` actuator IP allow-list is
  a good control but is string-parsed per request | *Release It* | precompute
  the CIDR set once at startup.
- **LOW** | common | `@SuppressWarnings("null")` were removed this session;
  re-run ECJ null-analysis to confirm zero residual warnings, and prefer
  narrow, justified suppressions only where a framework false-positive is
  unavoidable (EJ Item 27 generalised).

Deep pass should enumerate every public method in `common` for parameter
validation (EJ Item 49) and `@Nullable` correctness.

## 2. Java — base-station-service  _(needs deep per-file pass)_

Verified inline:

- DTOs are records with static factories/builders — good (EJ Item 2, Item 17).
- `RFMeasurement.FrequencyBand` was enriched with instance fields this session
  (EJ Item 34) — done.
- **HIGH** | `station/model/*` (JPA entities) | entities used directly across
  layers risk N+1 and `equals`/`hashCode` on mutable entities (EJ Item 10/11;
  Kleppmann ch.2) | verify fetch types are `LAZY`, add `@EntityGraph` where
  lists are read, and do not use generated-id entities in hash-based collections
  before persist.
- **MEDIUM** | `DeviceCommandService` | carries multiple `@SuppressWarnings`
  (`java:S2583/S2589`) for intentional null checks | keep, but document why each
  branch is reachable (defensive contract) — EJ Item 27.
- **MEDIUM** | geospatial search | confirm radius/bounding-box queries use
  indexed columns and validated numeric bounds (EJ Item 49).

## 3. Java — monitoring-service  _(largest Java module; needs deep per-file pass)_

Verified inline:

| path | sev | issue | book | fix |
|------|-----|-------|------|-----|
| `service/AlertingService.java` (685 LOC) | MEDIUM | approaching the 800-line ceiling; many alert-rule factory methods inline | EJ Item 15 (minimise scope) | extract the default-rule catalogue into a dedicated `DefaultAlertRules` provider |
| `service/DiagnosticSessionService.java` (667) | MEDIUM | large, mixes session lifecycle + AI-call orchestration | SRP | split orchestration from persistence |
| `validation/MetricValueValidator.java` (543) | LOW | large but cohesive (one validator) | — | acceptable; consider table-driven ranges |
| `controller/MonitoringController.java` (526) | MEDIUM | controller holds request/response DTOs as nested classes + batch logic | EJ Item 24 (favour static member classes) is fine, but the batch record loop belongs in the service | move `recordMetricsBatch` mapping into `MonitoringService` |
| `controller/MonitoringController.java` batch path | — | **already fixed this session**: band was dropped in batch ingest; field + regression test added | Newman (contracts) | see cross-service contract test in §11 |
| `config/*Migration.java` | LOW | `@EventListener(ApplicationReadyEvent)` migrations run on every boot | — | idempotent already; consider a versioned migration record to skip fast |

Deep pass: thread-safety of any in-memory caches/rule maps (EJ Item 78–82),
`Optional` return usage (EJ Item 55), and metric-label cardinality (Brazil).

## 4. Java — notification-service  _(needs deep per-file pass)_

- **HIGH** | `service/AsyncNotificationExecutor`, `NotificationService` | verify
  every `CompletableFuture` chain has `whenComplete`/`exceptionally` so a failed
  send is logged, not swallowed (EJ Item 69; Nygard: no silent failures) | audit
  each async path.
- **HIGH** | RabbitMQ consumers | confirm idempotency + dead-letter handling on
  redelivery (Nygard ch. steady-state; Newman) | add DLQ + idempotency key.
- **MEDIUM** | `integration/SlackService` and other `AlertIntegration` impls |
  external `RestTemplate`/HTTP calls need explicit connect/read timeouts
  (*Release It*: integration points) | set timeouts + circuit breaker.
- **MEDIUM** | integrations | likely DRY overlap across integration channels |
  extract a common `AlertIntegration` template (retry, timeout, formatting).

## 5. Java — auth-service + tmf-api  _(security-critical; needs deep per-file pass)_

- **HIGH** | auth JWT | verify algorithm is pinned (no `none`/alg-confusion),
  clock-skew tolerance bounded, and `exp`/`nbf` enforced (OAuth2iA ch.11) |
  assert in a focused test.
- **HIGH** | refresh tokens | confirm rotation + reuse-detection (a stolen
  refresh token must be single-use) (OAuth2iA ch.10) | add reuse-detection test.
- **MEDIUM** | auth lockout | confirm lockout counter is atomic under concurrent
  failed logins (EJ Item 78) | test with parallel attempts.
- **MEDIUM** | tmf-api | direct entity exposure is annotated as intentional for
  TMF conformance; confirm response shapes match TMF638/639/642 schemas | add a
  schema-conformance test per API.
- **INFO** | tmf-api | hardened this session (auth, actuator, CORS, prometheus
  registry) but **not deployed** (see §10) — its security is ready, its
  integration is not.

## 6. Go — edge-bridge  _(Harsanyi; needs deep per-file pass)_

Verified inline:

| path:line | sev | issue | book | fix |
|-----------|-----|-------|------|-----|
| `internal/device/manager.go:134` | LOW | `time.After` inside the `reconnect()` `for/select`; bounded here (waits on it each iteration) but idiomatically leaky | 100 Go Mistakes #76 | use `time.NewTimer` + `Stop()`, or `time.Ticker` |
| `internal/device/manager.go:284`, `bridge/bridge.go:201`, `adapter/netconf/adapter.go:496,541` | LOW | same `time.After`-in-select pattern | #76 | same |
| goroutines in `device/manager.go`, `bridge/bridge.go`, `adapter/manager.go`, `oran`, `netconf` | HIGH | **deep pass required**: verify every goroutine has a clear stop signal via `ctx` and cannot leak on shutdown | #62–68 (goroutines & context) | audit each `go` for ctx-cancellation + `WaitGroup` |
| cloud client (`internal/cloud/*`) | MEDIUM | confirm retry/backoff is bounded with jitter and the upload buffer has a cap (unbounded buffer = memory risk) | Nygard: bounded queues | cap buffer, drop-oldest with a logged counter |
| adapters (modbus/mqtt/oran/snmp/netconf) | MEDIUM | error wrapping consistency (`%w`) and typed sentinels | #48–50 | standardise on wrapped errors |

Good: band codec round-trips (tested), `omitempty` band JSON is correct.

## 7. C — device-protocol-c  _(Seacord; parses untrusted bytes — highest bar)_

Partial agent result before cutoff: *"metrics.c and protocol.c look solid;
transport files under review."* Verified posture: Makefile hardening is strong
(`-fstack-protector-strong`, `_FORTIFY_SOURCE=2`, `-Wformat-security`,
`-Werror`), and frame/metric parsing bounds-checks `payload_len` against
`MAX_PAYLOAD_SIZE`.

- **HIGH** _(needs deep pass)_ | `src/transport_*.c` | audit every `read`/`recv`
  return for short-read/`-1`/`0` handling and ensure no OOB on partial frames
  (Seacord ch.7 I/O; ch.2 strings) | verify each I/O return checked.
- **HIGH** _(needs deep pass)_ | `src/frame.c`, `src/protocol.c` | re-verify
  integer handling on all length/offset math for overflow/signedness (Seacord
  ch.5) | add explicit bounds asserts + fuzz the frame parser (fuzz/ exists —
  wire it into CI).
- **MEDIUM** | transport | confirm every `socket`/`fd`/`FILE` is closed on all
  error paths (Seacord ch.8 resource) | RAII-style goto-cleanup review.
- **MEDIUM** | tests | `transport*.c` are untested (unit); add a fake transport
  to exercise partial/oversized/malformed frames (Khorikov behaviour tests).

## 8. Python — ai-diagnostic  _(largest area, 19k LOC; biggest liability)_

Verified inline — **module size is the headline problem** (SRP; PEP 8 readability):

| path | LOC | sev | issue | fix |
|------|-----|-----|-------|-----|
| `service/diagnostic_service.py` | **2807** | HIGH | one module = Flask app + TCP server + routing + orchestration + wire protocol | split into `app/` (routes), `transport/` (tcp), `orchestration/` |
| `service/bi_report_generator.py` | 1251 | HIGH | report generation monolith | split by report type + a shared renderer |
| `virtual-basestation/mips_device.py` | 1158 | MEDIUM | simulator state + metrics + fault model + wire | extract fault model and metric catalogue |
| `service/predictive_maintenance.py` | 912 | MEDIUM | model + features + serving in one | separate feature engineering from serving |
| `service/self_healing.py` | 889 | MEDIUM | policy + execution + integration | split policy from actuation |
| `service/son_functions.py` | 752 | MEDIUM | many SON functions in one file | one module per SON function family |
| `service/drone_integration.py` | 741 | MEDIUM | — | decompose |
| `service/anomaly_detection.py` | 718 | MEDIUM | detection + scoring + I/O | separate detector from I/O |

Positives (verified): no bare `except`, no mutable default args, no `print()` in
`service/`, uses `logging`. Deep pass should add: type hints at module
boundaries, Flask input validation (schema per endpoint), and extract the ~8
duplicated "analyze → score → recommend" skeletons into a shared base
(huge DRY win). Most of these modules are **untested** — see §11.

## 9. Frontend — React/TypeScript + UX  _(needs deep per-file pass)_

Verified inline — **large page components** (Refactoring UI: compose small
pieces; React: container/presentational split):

| path | LOC | sev | fix |
|------|-----|-----|-----|
| `pages/AIDiagnostics.tsx` | 854 | MEDIUM | extract panels/cards into components; move data logic to hooks |
| `pages/FiveGDashboard.tsx` | 740 | MEDIUM | extract `BandSummaryCard`/`CellRow`/`MetricGauge` into files (some already inline) |
| `pages/SONRecommendations.tsx` | 713 | MEDIUM | split list/detail/actions |
| `pages/StationDetail.tsx`, `pages/PowerDashboard.tsx` | 684 | MEDIUM | extract sections |
| `components/DashboardComponents.tsx` | 633 | MEDIUM | this is a grab-bag; split one component per file |
| `pages/Metrics.tsx` | 609 | MEDIUM | extract chart + controls |

- **LOW** | frontend | only 7 `any`/`ts-ignore`/`eslint-disable` total — tighten
  those to real types where feasible.
- **UX (needs pass)** | apply *Refactoring UI* (spacing scale, fewer borders,
  clear hierarchy) and *Laws of UX* (Hick's law on dense dashboards; Jakob's law
  on conventional controls); verify empty/loading/error states exist on every
  data view; a11y sweep (labels, roles, contrast ≥4.5:1, keyboard nav).

## 10. Observability + Infrastructure / CI  _(needs deep per-file pass)_

Verified inline (several confirmed live this session):

- **HIGH** | CI (`.github/workflows/ci.yml`) | CI compiles/tests but **never
  builds or runs the Docker images** — this is why two Dockerfiles (ai-diagnostic
  `PYTHONPATH`, edge-bridge Go 1.21 vs go.mod 1.23) shipped broken and only the
  live bring-up caught them (Nygard: test what you deploy) | add a `docker
  compose build` + smoke-up job (or per-image build) to CI.
- **HIGH** | `docker-compose.yml` | **no per-service memory/CPU limits** — the
  earlier machine crash and the parallel-build spike trace to unbounded resource
  use (Nygard: bulkheads/limits) | add `deploy.resources.limits` and sensible
  `restart` policies.
- **HIGH** | `tmf-api` | built in the Maven reactor but has **no Dockerfile and
  is absent from compose and Helm** — dead integration (Newman: every service
  independently deployable) | add Dockerfile + compose + Helm, or explicitly
  mark it experimental and exclude from the reactor default.
- **MEDIUM** | Dockerfiles | verify all run as **non-root**, pin base tags (no
  `latest`), and have `.dockerignore`; the Java images are multi-stage (good) |
  add `USER` directives + healthchecks where missing.
- **MEDIUM** | secrets | MongoDB credential mismatch this session shows env/
  volume-init coupling is fragile | document the `.env` ↔ volume-init contract;
  fail fast with a clear message on auth mismatch.
- **MEDIUM** | Helm | verify every Deployment has resource requests/limits and
  liveness/readiness probes; add PDBs/HPAs consistently.
- **MEDIUM** | Prometheus/Grafana | confirm metric names/labels follow Brazil's
  conventions (no high-cardinality labels like raw IDs), histogram buckets suit
  the SLOs, and traces (Zipkin/Brave) propagate across **every** hop incl. the
  Python service (Majors: connect logs↔metrics↔traces via correlation id).
- **DONE this session** | `/actuator/prometheus` is now permitted for scraping
  platform-wide; tmf-api scrape target removed (was a dead target).

## 11. Tests + Documentation  _(Khorikov / GOOS / Newman; needs deep per-file pass)_

Test gaps (verified by absence):

- **HIGH** | cross-service | **no consumer-driven contract tests** between
  edge-bridge (producer of the metric JSON) and monitoring (consumer). The
  batch-`band` bug proves the gap: each side was unit-tested, the contract was
  not (Newman ch. testing; Pact-style) | add a contract test asserting the
  edge-bridge `MetricData` JSON deserialises into monitoring's batch DTO with
  band preserved.
- **HIGH** | Python | the large `service/*` modules (predictive_maintenance,
  self_healing, son_functions, bi_report_generator, anomaly_detection beyond the
  bits covered) are **largely untested**; behaviour tests should follow the
  decomposition in §8 (Khorikov: test observable behaviour of the new seams).
- **MEDIUM** | C | `transport_*.c` untested; add fake-transport behaviour tests
  and wire the existing `fuzz/` harness into CI (GOOS: grow tests with the code).
- **MEDIUM** | Go | some adapters (modbus/mqtt/oran/snmp) still lack tests where
  they contain pure parsing/mapping logic (netconf/types/transport now covered).
- **MEDIUM** | test quality | audit for mock-heavy tests that assert on
  interactions rather than outcomes (Khorikov: prefer output/state verification;
  GOOS: only mock types you own) — especially the Java `@WebMvcTest` mock
  setups.

Docs:

- **DONE this session** | API.md (band + catalog), ARCHITECTURE.md (trust model,
  band model, tmf-api status), TESTING.md (counts + Python row), PERFORMANCE.md.
- **MEDIUM** | docs-missing | no `CONTRIBUTING.md`, no ADRs (Architecture
  Decision Records) for the big calls (band-neutral model, gateway-header trust,
  Kubernetes-DNS over Eureka), no operational runbook | add ADRs + a runbook.
- **LOW** | docs | `testing/README.md` metric-type list predates the band-neutral
  contract; refresh to reference `/api/v1/metrics/catalog`.

---

## Suggested execution order (phased)

1. **Phase A — CRITICAL/HIGH correctness & safety**
   - C transport I/O + integer-math deep pass (Seacord) + wire the fuzzer into CI.
   - Go goroutine/context leak audit on shutdown paths (Harsanyi #62–68).
   - auth JWT/refresh deep pass (OAuth2iA) with focused tests.
   - notification async error-handling + RabbitMQ idempotency/DLQ.
2. **Phase B — build & contract safety nets** (prevents recurrence of this
   session's live bugs)
   - CI builds + smoke-runs the container images.
   - Consumer-driven contract test edge-bridge ↔ monitoring.
   - compose/Helm resource limits + probes.
3. **Phase C — structural maintainability**
   - Decompose the Python monoliths (§8) behind new behaviour tests.
   - Split the large frontend pages (§9) and Java services (§3/§4).
4. **Phase D — observability & docs**
   - Metric/label/trace hygiene (Brazil/Majors); correlation across all hops.
   - ADRs, runbook, CONTRIBUTING; doc refresh.
5. **Phase E — frontend/UX & polish**
   - Refactoring UI / Laws of UX pass; a11y; tighten residual `any`.

## Re-running the deep audit

When session budget resets, re-dispatch the 11 per-domain auditors (prompts and
book grounding are captured in this session) to replace each _(needs deep
per-file pass)_ block with exhaustive `path:line` rows. This document's phases,
severities, and book legend are the destination for those rows.
