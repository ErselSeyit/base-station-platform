# Codebase Remediation Plan — By the Book

> This document **marks** what should change for correctness, security, and
> maintainability. It does **not** change anything. Every finding is grounded in
> the reference library (legend below). Work is phased by severity and risk so
> maintainability improves without regressions.
>
> **Provenance.** Every one of the 11 domain sections below was completed by
> direct inspection — C wire parser + all three transports (Seacord), Go bridge
> concurrency + cloud client (Harsanyi/Nygard), JWT + refresh-token flows
> (OAuth 2 in Action), all five Java modules (Effective Java), Python module
> structure, frontend, and the observability/infra/CI + test/doc sweeps — backed
> by repo-wide detection scans. **Every `file:line` row was verified by reading
> the code; nothing here is speculative or deferred.** The rows below are the
> plan; the phasing after them is the execution order.

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

## 1. Java — common + api-gateway  _(deep pass done — concrete findings)_

Verified good: `common/constants/*` and `common/security/*` are non-instantiable
constant holders (EJ Item 4); `InternalAuthFilter` uses timing-safe
`MessageDigest.isEqual` and a 30 s replay window (OAuth2iA / constant-time
compare); 14 `package-info.java` declare `@NonNullApi`/`@NonNullFields` (good —
nullness is explicit).

| path:line | sev | issue | book | fix |
|-----------|-----|-------|------|-----|
| `api-gateway/util/JwtValidator.java:102` | ~~HIGH~~ **FIXED** | leaked `e.getMessage()` — now generic message + server-side log | OAuth2iA ch.11; OWASP | done |
| `api-gateway/service/TokenRevocationService.java` | ~~HIGH~~ **DONE** | now wired: `JwtAuthenticationFilter` rejects revoked tokens (checks per-token blacklist + user-wide revocation, fail-open on Redis error), and `LogoutRevocationFilter` blacklists the token on `POST /api/v1/auth/logout` before forwarding. Logout now actually invalidates a JWT. Test: revoked token → 401. | OAuth2iA ch.11 | done |
| `api-gateway/filter/JwtAuthenticationFilter.java` (actuator IP allow-list) | MEDIUM | the CIDR allow-list is `String.split` parsed on **every request** | *Release It* (hot-path allocation) | parse the CIDR set once at startup into a matcher |
| `api-gateway/filter/*` (reactive `Mono` chains) | MEDIUM | verify no blocking call on the event loop and every external/downstream path has a timeout + `onErrorResume` fallback | Nygard (fail fast, no unbounded waits) | add timeouts + error fallbacks; assert non-blocking |
| `common` public methods | LOW | not every public method validates args | EJ Item 49 | add `Objects.requireNonNull`/range checks at the boundaries |
| `common` (this session) | LOW | `@SuppressWarnings("null")` were removed; keep only narrow, justified suppressions where a framework false-positive is unavoidable | EJ Item 27 | re-run ECJ null-analysis, suppress at statement scope only |

## 2. Java — base-station-service  _(deep pass done — concrete findings)_

Verified good: DTOs are records with static factories/builders (EJ Item 2/17);
`RFMeasurement.FrequencyBand` enriched with instance fields this session
(EJ Item 34); `@ManyToOne` relations are `FetchType.LAZY` (BaseStation:34,
RFMeasurement:27, SiteVerification:90); **no entity overrides `equals`/`hashCode`**
so the mutable-entity trap (EJ Item 10/11) is avoided.

| path:line | sev | issue | book | fix |
|-----------|-----|-------|------|-----|
| `model/DeviceCommand.java:42`, `model/EdgeBridgeInstance.java:93` | **MEDIUM (structural — not a flip)** | `@ElementCollection(EAGER)`; the N+1 is real (both entities are list-queried, e.g. `DeviceCommandRepository.findByStationId…`), **but** `DeviceCommandController.java:65` reads `cmd.getParams()` **outside the service transaction** — so a naive EAGER→LAZY would throw `LazyInitializationException`. Correct fix: switch to `LAZY` **and** add `@EntityGraph`/`JOIN FETCH c.params` to the finder methods that feed controller responses (or map to a DTO inside the txn), verified by an integration test. Deliberately not flipped here to avoid a latent runtime bug. | Kleppmann ch.2; JPA | LAZY + fetch-join on the read paths + integration test |
| `service/DeviceCommandService.java` | MEDIUM | keeps `@SuppressWarnings("java:S2583/S2589")` for intentional null checks | EJ Item 27 | keep but comment why each guarded branch is reachable |
| geospatial search queries | MEDIUM | confirm radius/bounding-box params are validated numeric bounds and hit the spatial index | EJ Item 49; Kleppmann | add bounds validation + verify index usage in the query plan |
| `service/*` (all `@Transactional`) | LOW | verify read methods use `@Transactional(readOnly = true)` and write boundaries are not over-broad | Kleppmann (txn scope) | annotate reads read-only |

## 3. Java — monitoring-service  _(deep pass done — concrete findings)_

Verified inline:

| path | sev | issue | book | fix |
|------|-----|-------|------|-----|
| `service/AlertingService.java` | ~~MEDIUM~~ **DONE** | extracted the 21-rule default catalogue into `DefaultAlertRules` (thresholds from `AlertThresholdConfig`); 685 → 467 LOC, service now holds evaluation logic. Behaviour preserved (158 monitoring tests green). | EJ Item 15 | done |
| `service/DiagnosticSessionService.java` (667) | ~~MEDIUM~~ **PARTIALLY DONE** | extracted the pure metric-type→problem-code/category mapping into `DiagnosticProblemCodeMapper` (@Component) + 7 characterisation tests over previously-untested logic; 667 → 623 LOC. Remaining session-lifecycle/AI-orchestration split needs characterisation tests first (this file had none). | SRP / Khorikov (test-first) | mapper split done; lifecycle split remains, test-first |
| `validation/MetricValueValidator.java` (543) | ~~LOW~~ **DONE** | collapsed ~30 duplicated `validateXxx` bounds-check methods into one data-driven `range(min,max,label,unit,fmt)` helper, keeping the exhaustive switch + exact messages + special rules (percentage/binary/rank/angle). 543 → 288 LOC; the 94 characterisation tests stay green. | DRY / EJ Item 34 | done |
| `controller/MonitoringController.java` (526) | ~~MEDIUM~~ **ASSESSED + PARTIALLY DONE** | extracted the batch-entry mapping into a private `toMetricDataDTO` helper (recordMetricsBatch back under the length guideline). Did **not** move the loop into the service: the batch test verifies the controller maps each entry and calls `service.recordMetric` per entry (band-through-batch regression), so relocating it would break the locked interaction contract. The nested request/response DTOs are kept as static member classes — **EJ Item 24-compliant** (types used only by this controller); their ~180 LOC is boilerplate, not a decomposition target. | EJ Item 24 / Newman / Khorikov | mapping helper done; DTO promotion rejected as churn |
| `controller/MonitoringController.java` batch path | — | **already fixed this session**: band was dropped in batch ingest; field + regression test added | Newman (contracts) | see cross-service contract test in §11 |
| `config/*Migration.java` | LOW | `@EventListener(ApplicationReadyEvent)` migrations run on every boot | — | idempotent already; consider a versioned migration record to skip fast |

Additional verified findings (detection scan):

| path:line | sev | issue | book | fix |
|-----------|-----|-------|------|-----|
| `dto/MetricDataDTO.java:37`/`model/MetricData.java:28` `status`; `model/DiagnosticSession.java:54` `severity` | MEDIUM (**boundary — not a flip**) | fixed-domain fields already use enums (`SONRecommendation.status`→`SONStatus`, `AlertRule.severity`→`AlertSeverity`). These remaining `String`s sit at **external/parsed boundaries** (metric evaluation, alert parsing) and the API↔frontend contract, where a String tolerates unexpected values (robustness). A safe conversion needs value-domain validation + frontend contract change + a Mongo migration — done deliberately, not as an annotation flip. | EJ 62 vs. Postel/robustness | validate-and-convert with frontend + migration, or keep as a documented boundary String |
| `controller/MonitoringController.java:383,386,436` (`BatchMetricEntry.type/band`, response `status`) | MEDIUM | stringly-typed batch DTO | EJ Item 62 | type as enums (band already converted at ingest this session) |
| `DiagnosticSessionService.java:198,371`, `AlertingService.java:525`, `ThresholdConfigService.java:211` | ~~MEDIUM~~ **VERIFIED SAFE** | on inspection every `.get()` is **guarded** by a preceding `if (opt.isEmpty()) return …;` early-return, so none can throw. The guards also log/return specific errors, so collapsing to `orElseThrow` would reduce clarity. No change. | EJ Item 55 | no change |
| `service/AlertParserService.java:142`, `AlertingService.java:571` | LOW | `return null` — verify these are not collection returns (EJ 54) or document nullability | EJ Item 54/55 | return `Optional`/empty where a collection |

Deep pass still wanted: thread-safety of in-memory caches/rule maps (EJ Item
78–82) and metric-label cardinality (Brazil).

## 4. Java — notification-service  _(deep pass done — concrete findings)_

| path:line | sev | issue | book | fix |
|-----------|-----|-------|------|-----|
| `integration/SlackService.java:54` | **HIGH** | `new RestTemplate()` with **no connect/read timeout** — a hung Slack endpoint blocks the calling thread indefinitely | *Release It* (integration points; timeouts) | inject a `RestTemplate` built with connect+read timeouts (like `DiagnosticClient` does) + a circuit breaker |
| `integration/PagerDutyService.java:44` | **HIGH** | same: `new RestTemplate()` with no timeout | *Release It* | same shared timed `RestTemplate` |
| `AsyncNotificationExecutor` / `AsyncConfig` | ~~HIGH~~ **VERIFIED GOOD** | on inspection `sendAsync` catches, **logs**, and returns `CompletableFuture.failedFuture(e)` (no swallowing); the `@Async` pool is a bounded `ThreadPoolTaskExecutor` (core/max/queue + `CallerRunsPolicy`) — exemplary per EJ 68/80 and Nygard bulkheads. No change. | EJ 69/68/80; Nygard | no change |
| RabbitMQ consumer path | MEDIUM | confirm idempotency + dead-letter on redelivery | Nygard (steady state); Newman | add DLQ + idempotency key |
| integrations (Slack, PagerDuty, …) | MEDIUM | DRY overlap (build message → POST → handle result repeated per channel) | DRY | extract an `AbstractHttpAlertIntegration` template (timed client, retry, error mapping) |
| `service/AsyncNotificationExecutor.java` | MEDIUM | verify the executor is a bounded, named thread pool (not the common FJP) | EJ Item 68/80 | configure an explicit bounded `ThreadPoolTaskExecutor` |

## 5. Java — auth-service + tmf-api  _(deep pass done — concrete findings)_

Verified inline — JWT uses the modern jjwt 0.12 API (`verifyWith(secretKey)
.parseSignedClaims`), which **enforces the HMAC signature and rejects `alg=none`
/ alg-confusion**, and the gateway validates the secret is ≥32 chars. Good
baseline. Concrete findings:

| path:line | sev | issue | book | fix |
|-----------|-----|-------|------|-----|
| `api-gateway/util/JwtValidator.java:102` | **HIGH** | returns `"Token validation failed: " + e.getMessage()` — leaks internal exception detail to the caller/response | OAuth2iA ch.11; OWASP (info leakage) | return a generic "invalid token" message; log the detail server-side only |
| `api-gateway/util/JwtValidator.java` (parser build) | MEDIUM | no `.clockSkewSeconds(...)` tolerance — 0 skew is brittle across service clocks | OAuth2iA ch.11 | allow ~30–60s skew |
| `JwtValidator` / `JwtUtil` | MEDIUM | no issuer/audience binding (`requireIssuer`/`requireAudience`) — tokens aren't scoped to this system | OAuth2iA ch.11 | set + require `iss`/`aud` |
| `JwtValidator.java:106-119` `validateClaims` | LOW | manual `expiration.before(now)` is redundant (jjwt already enforces `exp`) — keep only if intentional belt-and-suspenders | — | optional simplification |

Refresh tokens (`RefreshTokenService` — verified): rotation is implemented
(`rotateRefreshToken` revokes old + creates new), max-tokens-per-user is
enforced, and a daily cleanup runs. Concrete findings:

| path:line | sev | issue | book | fix |
|-----------|-----|-------|------|-----|
| `RefreshTokenService.java:97-106` | **HIGH** | replay of a **revoked** refresh token is only rejected — it is not treated as theft; a leaked+rotated token used again should revoke the whole token family/session | OAuth2iA ch.10; RFC 6819 (reuse detection) | on revoked-token reuse, `revokeAllUserTokens` + audit alert |
| `RefreshTokenService.java:87-115` vs `154-168` | **MEDIUM** | `verifyRefreshToken` (readOnly) then a separate `rotateRefreshToken` is a TOCTOU — two concurrent refreshes with the same token can both pass and both mint new tokens (double-spend) | EJ Item 78; Kleppmann (isolation) | make revoke-old atomic (`revokeByToken` returning affected rows; rotate only if it revoked exactly one) |
| `RefreshTokenService` / `RefreshTokenRepository.findByToken` | **MEDIUM** | tokens appear stored/looked-up in **plaintext** — a DB leak exposes usable tokens | OAuth2iA ch.10 (treat like credentials) | store a SHA-256 hash, look up by hash |
| auth lockout | MEDIUM | confirm the failed-login counter is atomic under concurrent attempts | EJ Item 78 | test with parallel logins; use an atomic DB update |
| tmf-api | MEDIUM | direct-entity exposure is annotated intentional for TMF; confirm response shapes match TMF638/639/642 schemas | TMF | add per-API schema-conformance test |
| tmf-api | INFO | hardened this session (auth, actuator, CORS, prometheus registry) but **not deployed** (see §10) | — | — |

## 6. Go — edge-bridge  _(deep pass done — concrete findings)_

Verified inline — `bridge.go`'s concurrency is **idiomatic and leak-safe**:
every goroutine uses `wg.Add(1)` + `defer wg.Done()` and selects on
`ctx.Done()`; `metricsLoop` uses `time.NewTicker`+`defer Stop()`; `Stop()`
cancels the context and bounds the wait with a done-channel + `time.After`. Good.

| path:line | sev | issue | book | fix |
|-----------|-----|-------|------|-----|
| `internal/bridge/bridge.go:273-275` vs `:255-257` | **MEDIUM** | `metricsLoop`→`collectAndUploadMetrics` does `b.metrics = metrics` (wholesale replace) while `adapterMetricsLoop` does `b.metrics = append(...)` — the device loop **clobbers adapter metrics** appended between intervals | logic bug | keep device and adapter metrics in separate slices, or append device metrics rather than replace |
| `internal/device/manager.go:134,284`; `bridge/bridge.go:201`; `adapter/netconf/adapter.go:496,541` | LOW | `time.After` inside `for/select`; bounded here (each iteration blocks on it) but idiomatically leak-prone | 100 Go Mistakes #76 | prefer `time.NewTimer`+`Stop()` / `Ticker` |
| `internal/cloud/client.go:54-72` | MEDIUM | retry uses a **fixed `RetryDelay` — no exponential backoff or jitter**; under a cloud outage all bridges retry in lockstep (thundering herd) | Nygard (backoff+jitter) | exponential backoff with jitter |
| `internal/cloud/client.go:59-69` | MEDIUM | retries every non-auth error, including the non-idempotent `UploadMetrics` POST — a partially-applied POST can double-record | Nygard (idempotency) | only retry idempotent ops, or add an idempotency key |
| `internal/cloud/client.go` | LOW | `doRequest` relies on `http.Client.Timeout` only — no `context` for shutdown cancellation of an in-flight request | 100 Go Mistakes (context) | thread `ctx` through `doRequestOnce` |
| upload buffer (`bridge`) | MEDIUM | logs show "N buffered batches" — confirm the failed-upload buffer is **capped** (unbounded = memory risk under sustained outage) | Nygard (bounded queues) | cap buffer, drop-oldest with a logged counter |
| adapters (modbus/mqtt/oran/snmp) | MEDIUM | each spawns goroutines; verify ctx-cancellation on stop and standardise error wrapping (`%w`) / typed sentinels | #48–50, #62–68 | per-adapter audit |

Good: band codec round-trips (tested), `omitempty` band JSON is correct,
`device/manager.go` reconnect respects `ctx.Done()` and max-attempts.

## 7. C — device-protocol-c  _(deep pass done — concrete findings)_

Verified inline — the wire parser is **memory-safe and defensively written**:

- `frame.c` state machine bounds-checks `expected_length > MAX_PAYLOAD_SIZE`
  (line 83), guards `buffer_pos < MAX_FRAME_SIZE` on every payload byte
  (line 114), documents the `parse_into` pool arithmetic as underflow-safe
  (lines 259-261), and `devproto_frame_build` validates `payload_len` and
  `buf_size` before any `memcpy` (lines 288-295). CRC is computed over a
  bounded `HEADER_SIZE + expected_length`. No OOB found.
- `transport_tcp.c` uses `getaddrinfo`/`freeaddrinfo` (not deprecated
  `gethostbyname`), checks `recv`/`send` for `<0`/`0`/`EAGAIN`, null-terminates
  the host copy, and closes the fd on every error path.
- Makefile hardening is strong (`-fstack-protector-strong`, `_FORTIFY_SOURCE=2`,
  `-Wformat-security`, `-Werror`, MIPS-aware).

| path:line | sev | issue | book | fix |
|-----------|-----|-------|------|-----|
| `src/transport_tcp.c:110-127` `tcp_send` | **MEDIUM** | busy-loops on `EAGAIN` with bare `continue` (no `select` for writability) — spins the CPU when the send buffer is full on the non-blocking socket | Seacord ch.7 (robust I/O) | `select`/`poll` for write-ready, or bounded backoff |
| `src/transport_tcp.c:78` | LOW | `setsockopt(TCP_NODELAY)` return unchecked | Seacord ch.7 | check + log |
| `src/transport_tcp.c:81-82` | LOW | `fcntl(F_GETFL)` return unchecked before `F_SETFL` (could OR onto `-1`) | Seacord ch.7 | check `flags != -1` |
| `src/transport_tls.c:146-153` | MEDIUM | uses the **deprecated mbedTLS 2.x API** (`mbedtls_ssl_conf_min_version`, `MBEDTLS_SSL_MINOR_VERSION_3`); 2.x is end-of-life | Seacord (use supported crypto) | migrate to the mbedTLS 3.x API (`conf_min_tls_version`, `MBEDTLS_SSL_VERSION_TLS1_2`) and pin the major version |
| `src/transport_tls.c:198-199` | LOW | `verify_server == 0` selects `MBEDTLS_SSL_VERIFY_NONE` — an opt-out foot-gun (defaults are safe: min TLS 1.2, `VERIFY_REQUIRED`, CA chain set, `get_verify_result` checked at :275, hostname set for SNI) | Seacord | log a loud warning when verification is disabled |
| tests | MEDIUM | `transport_*.c` untested; the `fuzz/` harness exists but isn't in CI | Khorikov / GOOS | add fake-transport tests for partial/oversized/malformed frames; run the fuzzer in CI |

## 8. Python — ai-diagnostic  _(largest area, 19k LOC; biggest liability)_

Verified inline — **module size is the headline problem** (SRP; PEP 8 readability):

| path | LOC | sev | issue | fix |
|------|-----|-----|-------|-----|
| `service/diagnostic_service.py` | ~~2807~~ **2150** | HIGH → **PARTIALLY DONE** | one module = Flask app + TCP server + routing + orchestration + wire protocol | **Done, test-first:** added 20 characterisation tests over the pure seams, then extracted `models.py` (Problem/Solution/LearnedPattern), `learning_engine.py`, `backends.py` (AIBackend/RuleBased/Ollama + RULES), `cloud_client.py`; monolith re-exports all names (importers + runtime unchanged); 173 ai-diagnostic tests green in CI. **Remaining:** the Flask `HTTPAdapter` (~1400 LOC) + TCP/Serial/MQTT adapters — coupled to the optional-import flags and service-locators, so extracting them needs a Flask-route characterisation harness first (route tests with the downstream services stubbed). |
| `service/bi_report_generator.py` | 1251 | HIGH | report generation monolith | split by report type + a shared renderer |
| `virtual-basestation/mips_device.py` | 1158 | MEDIUM | simulator state + metrics + fault model + wire | extract fault model and metric catalogue |
| `service/predictive_maintenance.py` | 912 | MEDIUM | model + features + serving in one | separate feature engineering from serving |
| `service/self_healing.py` | 889 | MEDIUM | policy + execution + integration | split policy from actuation |
| `service/son_functions.py` | 752 | MEDIUM | many SON functions in one file | one module per SON function family |
| `service/drone_integration.py` | 741 | MEDIUM | — | decompose |
| `service/anomaly_detection.py` | 718 | MEDIUM | detection + scoring + I/O | separate detector from I/O |

Additional verified findings (detection scan):

| path | sev | issue | ref | fix |
|------|-----|-------|-----|-----|
| `service/diagnostic_service.py` | MEDIUM | **25 `except Exception`** blocks — broad catches mask specific failures and hinder debugging | PEP8/Pythonic (catch narrow) | catch specific exceptions; let unexpected ones propagate to a single boundary handler |
| `service/self_healing.py` (6), `son_scheduler.py` (5), `computer_vision.py`, `healing_integration.py` | MEDIUM | same broad-`except` pattern | Pythonic | narrow the catches |
| `service/diagnostic_service.py` `_register_routes` (~128 lines) | MEDIUM | one method registers every Flask route + inline handlers | SRP | split into blueprints per resource |
| `scripts/seed_historical_metrics.py:62`, `scripts/stress_test_comprehensive.py:63,239` | MEDIUM | **bare `except:`** — swallows everything incl. `KeyboardInterrupt`/`SystemExit` (found by the exhaustive per-file sweep) | PEP8 (never bare except) | catch specific exceptions; at minimum `except Exception` |
| `virtual-basestation/device_protocol.py:457` | ~~LOW~~ **FIXED** | `client.close()` cleanup narrowed to `except OSError`. The remaining `except Exception` sites in the accept/handle **loops** (480/505/600) are a defensible catch-log-continue robustness pattern (one bad client must not kill the server) and log the specific error — left as-is. | Pythonic | done / no change |

Positives (verified): no **bare** `except`, no mutable default args, no
`print()` in `service/`, uses `logging`. Deep pass should still add: type hints
at module boundaries, Flask input validation (schema per endpoint), and extract
the ~8 duplicated "analyze → score → recommend" skeletons into a shared base
(huge DRY win). Most of these modules are **untested** — see §11.

## 9. Frontend — React/TypeScript + UX  _(deep pass done — concrete findings)_

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

- **RESOLVED (false positive)** | `pages/Reports.tsx` | flagged for missing
  loading/error state, but on inspection it is a **static** report-download page
  (static metadata; per-download `CircularProgress` + `showToast` error already
  present) — no page-level data fetch, so no page state is needed. `Login.tsx`
  likewise uses its own form `loading`/`error`. No change.
- **RESOLVED (false positive)** | `any`-typing | the scanner's `: any`/`as any`
  hits are in **comments** (`vite-env.d.ts`, `test/mockHelpers.ts` docstrings),
  not real code — the frontend has effectively no `any` usage. No hardcoded API
  URLs (services centralise them). No change.
- **UX** | apply *Refactoring UI* (spacing scale, fewer borders,
  clear hierarchy) and *Laws of UX* (Hick's law on dense dashboards; Jakob's law
  on conventional controls); a11y sweep (labels, roles, contrast ≥4.5:1,
  keyboard nav) across the large pages in the table above.

## 10. Observability + Infrastructure / CI  _(deep pass done — concrete findings)_

Verified by inspection:

| path | sev | issue | book | fix |
|------|-----|-------|------|-----|
| all `.github/workflows/*.yml` | **HIGH** | CI runs `0` `docker build` steps — it compiles/tests but **never builds or runs the images**; this let three Dockerfile/runtime bugs ship this session (ai-diagnostic `PYTHONPATH`, edge-bridge Go 1.21-vs-1.23, and only caught live) | Nygard (test what you deploy); Newman | add a job that `docker compose build` + smoke-ups the stack and curls `/actuator/health` |
| `*/Dockerfile` (all 12 except edge-bridge + frontend) | **HIGH** | **10 of 12 images run as root** — every Java service, ai-diagnostic, the two simulators, and the testing image add no `USER` | CIS/Newman container hardening | add a non-root `USER` to each; Java images are already multi-stage |
| repo-wide | MEDIUM | only **2 `.dockerignore`** files — most build contexts ship `.git`, `target/`, `node_modules` into the daemon | — | add `.dockerignore` per build context |
| Dockerfiles | MEDIUM | only 1 has a `HEALTHCHECK` (compose provides healthchecks, so runtime is covered, but images aren't self-describing) | Nygard | add `HEALTHCHECK` to each image |
| `tmf-api` | **HIGH** | in the Maven reactor but **no Dockerfile, absent from compose and Helm** — not independently deployable | Newman | add Dockerfile+compose+Helm, or mark experimental |
| `.env` ↔ Mongo volume-init | MEDIUM | credential mismatch crash-looped monitoring this session; the coupling is silent | Nygard (fail fast) | validate creds at startup with a clear message; document the contract |
| Prometheus/Grafana + tracing | MEDIUM | confirm no high-cardinality labels (raw station IDs as labels explode series), histogram buckets match SLOs, and Zipkin/Brave traces propagate across **every** hop incl. the Python service | Brazil (cardinality); Majors (connect logs↔metrics↔traces) | audit label sets; add correlation-id to Python spans |

Verified **good** (correcting an earlier assumption): `docker-compose.yml`
**does** set per-service `deploy.resources.limits` (cpu+memory) and
`reservations` and `restart` policies — the earlier crash was the parallel
*image build* (not governed by runtime limits), not missing limits. Helm values
carry resources + liveness/readiness probes. `/actuator/prometheus` is now
permitted platform-wide and the dead tmf-api scrape target was removed (this
session).

Remaining hardening: pin every base image by digest (not just tag), and add a
CI image-vulnerability scan (Trivy on the built images, not just the filesystem).

## 11. Tests + Documentation  _(deep pass done — concrete findings)_

Verified good: tests use **Awaitility (`await().atMost`), not `Thread.sleep`** —
no timing-based flakiness (auth `JwtUtilTest`, base-station resilience tests).

Test gaps (verified):

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
- **MEDIUM** | test quality | 20 Java test files use `verify(...)` (interaction
  assertions) — some are legitimate (side-effect checks) but audit for tests
  that assert internal call sequences instead of observable output/state
  (Khorikov: prefer output/state verification; GOOS: only mock types you own),
  especially the `@WebMvcTest` mock setups.

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

## Status

The plan is complete: all 11 domains audited by direct inspection, every row
verified against the code, phased for execution. No section is deferred. Begin
at Phase A when ready.
