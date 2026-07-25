# ADR 0003 — Kubernetes DNS instead of a service registry

## Status
Accepted.

## Context
The platform originally used Eureka for service discovery, which adds a registry
to run, register with, and keep healthy.

## Decision
Use Kubernetes DNS: services address each other by service name
(`http://auth-service:8084`). NGINX Ingress handles external routing.

## Consequences
- No Eureka to operate; fewer moving parts.
- Discovery is native to the orchestrator; local `docker compose` uses the same
  service-name addressing.
- Cross-cluster / non-Kubernetes deployment would need a different mechanism.
