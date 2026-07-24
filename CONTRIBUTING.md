# Contributing

This is a polyglot monorepo (Java · Go · C · Python · TypeScript). Each language
has its own build and test loop; CI runs all of them.

## Build & test per language

| Area | Build | Test |
|------|-------|------|
| Java (7 modules) | `mvn -q compile` | `mvn test -Ddemo.mode=true` (per module: `mvn test -pl <module> -am`) |
| Go (edge-bridge) | `cd edge-bridge && go build ./...` | `go test -race ./...` |
| C (device-protocol-c) | `make -C device-protocol-c` | `make -C device-protocol-c test` |
| Python (ai-diagnostic) | — | `cd ai-diagnostic && python -m pytest -q` |
| Frontend | `cd frontend && npx tsc --noEmit` | `npx vitest run` (+ `npx eslint src --max-warnings=0`) |

## The rule for changes

No change lands without a behaviour test that would fail before and pass after
(Khorikov, *Unit Testing*). Refactors land under an already-green suite, one
behaviour-preserving step at a time. Prefer fakes/stubs over mocks and verify
observable output/state rather than internal interactions.

## Conventions

- **Commits**: `type: summary` (`feat|fix|refactor|docs|test|chore|perf|ci`),
  imperative mood, explain the *why* in the body.
- **Branches**: never commit to `master`; branch first, open a PR, get CI green.
- **Immutability & null-safety**: prefer immutable data; validate inputs at
  boundaries; do not use blanket `@SuppressWarnings("null")` (narrow, justified
  suppressions only). See [REMEDIATION_PLAN.md](REMEDIATION_PLAN.md) for the
  book-grounded standards this codebase follows.
- **Security**: never hardcode secrets; the gateway is the only internet-facing
  service and it validates JWTs (issuer-bound); downstream services trust the
  `X-User-*` headers only behind the `InternalAuthFilter` HMAC gate.

## Architecture decisions

Significant decisions are recorded as ADRs under [docs/adr/](docs/adr/). Add a
new numbered ADR when you make a decision that future contributors would
otherwise have to reverse-engineer.

## Before opening a PR

1. All language suites pass locally (table above).
2. New/changed behaviour is covered by a test.
3. No secrets, no debug prints, no dead code left behind.
4. Docs updated if the change alters an API, the wire protocol, or deployment.
