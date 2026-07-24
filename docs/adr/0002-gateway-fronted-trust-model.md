# ADR 0002 — Gateway-fronted trust model

## Status
Accepted.

## Context
Downstream services need the authenticated user's identity and role, but must
not each re-validate the JWT, and must not be spoofable if reached directly.

## Decision
The API Gateway validates the JWT (issuer-bound, signature, expiry), then
forwards `X-User-Name`/`X-User-Role` headers plus an HMAC-SHA256-signed
`X-Internal-Auth` header. The shared `InternalAuthFilter` (in `common`) runs
first in every service and rejects any request whose `X-Internal-Auth` signature
is missing/invalid — so a client cannot spoof `X-User-Role: ADMIN` by calling a
service directly. Services build their Spring `Authentication` from the headers
only because that gate has passed.

## Consequences
- Services must scan the `common` package so `InternalAuthFilter` registers.
- The gateway is the only internet-facing service.
- Token revocation is not yet enforced at the gateway (see REMEDIATION_PLAN.md).
