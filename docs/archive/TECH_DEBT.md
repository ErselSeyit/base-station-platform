# Technical Debt & Known Issues

This document tracks known technical debt, architectural limitations, and issues that should be addressed in future iterations of the Base Station Platform.

## Table of Contents

- [Critical Issues](#critical-issues)
- [Security Concerns](#security-concerns)
- [Kubernetes Deployment](#kubernetes-deployment)
- [Architecture & Design](#architecture--design)
- [Database & Persistence](#database--persistence)
- [Observability & Monitoring](#observability--monitoring)
- [Frontend](#frontend)
- [DevOps & Operations](#devops--operations)

---

## Critical Issues

### 1. Hardcoded Credentials in Seed Scripts

**Severity:** High
**Files:** `init-db/auth-seed.sql`, `init-db/notification-seed.sql`, `generate-secrets.sh`

**Issue:**
- Default admin credentials (`admin/admin`) are hardcoded in seed scripts
- Secrets generation script creates predictable development credentials
- No mechanism to rotate credentials post-deployment

**Impact:**
- Production deployments would have known credentials
- Cannot easily change default passwords without modifying seed scripts

**Recommended Fix:**
- Create separate seed scripts for dev/staging/production
- Use environment variables or secret injection for initial credentials
- Implement credential rotation mechanism
- Add password change requirement on first login for admin accounts

---

### 2. Manual Database Initialization Required

**Severity:** High
**Context:** Kubernetes deployments start with empty databases

**Issue:**
- Databases must be manually seeded using `kubectl exec` commands
- No automated initialization on first deployment
- Easy to forget seeding steps, leading to non-functional deployment

**Impact:**
- Login fails without auth database seeding
- Metrics page empty without MongoDB seeding
- New deployments require manual intervention

**Recommended Fix:**
- Implement init containers in Kubernetes that seed databases automatically
- Use Flyway/Liquibase for PostgreSQL schema migrations
- Create MongoDB initialization job that runs on first deployment
- Add readiness probes that check for required seed data

---

## Security Concerns

### 3. Secrets in Git Repository

**Severity:** High
**Files:** `.env` files, potentially checked into git

**Issue:**
- `.env` file contains database passwords, JWT secrets, and API keys
- Risk of accidental commit to version control

**Current Mitigation:**
- `.gitignore` includes `.env`

**Recommended Fix:**
- Use external secret management (HashiCorp Vault, AWS Secrets Manager, K8s Sealed Secrets)
- Document secret rotation procedures
- Add pre-commit hooks to prevent secret commits
- Implement secret scanning in CI/CD pipeline

---

### 4. JWT Secret Configuration

**Severity:** Medium
**Files:** `k8s/secrets.yaml`, `api-gateway` service

**Issue:**
- JWT secret is base64-encoded but not encrypted
- Same secret used across all environments
- No key rotation mechanism

**Recommended Fix:**
- Implement JWT key rotation
- Use asymmetric keys (RS256) instead of symmetric (HS256)
- Store private keys in secure vault
- Implement token revocation mechanism

---

### 5. CORS Configuration Too Permissive

**Severity:** Medium
**Files:** `k8s/app-services.yaml` (line 381)

**Issue:**
```yaml
CORS_ALLOWED_ORIGINS: http://localhost:3000,http://localhost:80,http://localhost,http://localhost:30000
```
- Multiple localhost ports allowed
- No environment-specific CORS configuration

**Recommended Fix:**
- Use environment variables for CORS origins
- Restrict to specific domains in production
- Remove development origins from production builds

---

## Kubernetes Deployment

### 6. Eureka Server Deployed But Disabled

**Severity:** Medium
**Files:** `k8s/app-services.yaml` (Eureka deployment)

**Issue:**
- Eureka server is deployed to Kubernetes but all services have `EUREKA_CLIENT_ENABLED: "false"`
- Wastes cluster resources (1 CPU, 1GB memory)
- Kubernetes DNS provides native service discovery

**Recommended Fix:**
- Remove Eureka deployment from `k8s/app-services.yaml`
- Create separate Docker Compose profile for Eureka (Docker Compose only)
- Update documentation to clarify Eureka is Docker Compose-only

---

### 7. Image Tags Using "latest"

**Severity:** Medium
**Files:** `k8s/databases.yaml`, `k8s/app-services.yaml`

**Issue:**
- All application images use `:latest` tag
- Database images use `mongo:latest`, `redis:alpine`
- No version pinning leads to unpredictable deployments

**Impact:**
- Cannot rollback to specific versions
- Kubernetes may pull different versions on different nodes
- No reproducible builds

**Recommended Fix:**
- Tag application images with git commit SHA or semantic version
- Pin database images to specific versions (e.g., `mongo:8.0.4`, `redis:7.4-alpine`)
- Implement image versioning in CI/CD pipeline

---

### 8. No Health Checks or Readiness Probes

**Severity:** High
**Files:** All deployments in `k8s/app-services.yaml`

**Issue:**
- Deployments lack `livenessProbe` and `readinessProbe`
- Kubernetes cannot detect unhealthy pods
- Traffic may be routed to pods that aren't ready

**Impact:**
- Failed deployments may appear successful
- Users may encounter 503 errors during rollouts
- Crashed containers remain in service

**Recommended Fix:**
Add probes to all services:
```yaml
livenessProbe:
  httpGet:
    path: /actuator/health
    port: 8080
  initialDelaySeconds: 60
  periodSeconds: 10
readinessProbe:
  httpGet:
    path: /actuator/health/readiness
    port: 8080
  initialDelaySeconds: 30
  periodSeconds: 5
```

---

### 9. Resource Limits May Be Inappropriate

**Severity:** Medium
**Files:** `k8s/app-services.yaml`

**Issue:**
- All microservices have identical resource limits:
  ```yaml
  limits:
    cpu: "1"
    memory: 1Gi
  requests:
    cpu: "500m"
    memory: 512Mi
  ```
- Not tuned to actual service requirements
- May lead to over-provisioning or OOMKills

**Recommended Fix:**
- Profile each service under load
- Adjust limits based on actual usage patterns
- Implement Vertical Pod Autoscaler (VPA) for recommendations
- Add Horizontal Pod Autoscaler (HPA) for high-traffic services

---

### 10. emptyDir Volumes - Data Loss on Pod Restart

**Severity:** High
**Files:** `k8s/databases.yaml`

**Issue:**
- All database deployments use `emptyDir` volumes
- Data is lost when pods restart or are rescheduled

**Impact:**
- Database state is ephemeral
- Not suitable for production use
- All seed data must be re-applied after pod restart

**Recommended Fix:**
- Use PersistentVolumeClaims (PVC) for all databases
- Configure appropriate StorageClass (e.g., `local-path`, `ebs`, `gce-pd`)
- Implement backup/restore procedures
- Document disaster recovery process

Example:
```yaml
volumes:
- name: postgres-storage
  persistentVolumeClaim:
    claimName: postgres-auth-pvc
```

---

### 11. No Rolling Update Strategy Configuration

**Severity:** Medium
**Files:** `k8s/app-services.yaml`

**Issue:**
- Deployments use default rolling update strategy
- No `maxUnavailable` or `maxSurge` configuration
- May cause service disruption during updates

**Recommended Fix:**
```yaml
spec:
  replicas: 2
  strategy:
    type: RollingUpdate
    rollingUpdate:
      maxUnavailable: 0
      maxSurge: 1
```

---

## Architecture & Design

### 12. Tight Coupling Between Services

**Severity:** Medium

**Issue:**
- Services communicate directly via HTTP
- No circuit breakers or retry logic
- Failures cascade through the system

**Recommended Fix:**
- Implement Resilience4j circuit breakers
- Add retry policies with exponential backoff
- Consider service mesh (Istio, Linkerd) for advanced traffic management
- Implement bulkhead pattern to isolate failures

---

### 13. Multiple Database Technologies

**Severity:** Low
**Context:** PostgreSQL (3 instances), MongoDB, Redis

**Issue:**
- Four different database types increase operational complexity
- Each requires separate backup, monitoring, and expertise
- MongoDB used only for metrics storage (could use PostgreSQL with JSONB)

**Trade-offs:**
- MongoDB provides better query performance for time-series data
- Redis essential for caching and rate limiting
- Separate PostgreSQL instances provide service isolation

**Recommended Review:**
- Evaluate TimescaleDB (PostgreSQL extension) for metrics
- Consider consolidating to 2 technologies: PostgreSQL + Redis
- Keep current design if query performance is critical

---

### 14. No API Versioning Strategy

**Severity:** Medium
**Files:** Gateway routes use `/api/v1/`

**Issue:**
- API version hardcoded in routes
- No clear versioning or deprecation policy
- Breaking changes would require client updates

**Recommended Fix:**
- Document API versioning strategy
- Support multiple API versions simultaneously
- Implement deprecation warnings in headers
- Create API changelog

---

### 15. RabbitMQ Deployed But Underutilized

**Severity:** Low
**Files:** `k8s/databases.yaml` (RabbitMQ deployment)

**Issue:**
- RabbitMQ is deployed but only used for basic pub/sub
- Could be replaced with simpler event bus
- Adds operational overhead

**Recommended Review:**
- Evaluate if RabbitMQ features are needed (DLQ, routing, etc.)
- Consider simpler alternatives (Redis Streams, Kafka)
- Document event schemas and message contracts

---

## Database & Persistence

### 16. No Database Backup Strategy

**Severity:** Critical

**Issue:**
- No automated backups configured
- No disaster recovery plan
- No point-in-time recovery capability

**Recommended Fix:**
- Implement automated daily backups using CronJobs
- Store backups in remote object storage (S3, GCS)
- Test restore procedures regularly
- Document RPO (Recovery Point Objective) and RTO (Recovery Time Objective)

Example CronJob:
```yaml
apiVersion: batch/v1
kind: CronJob
metadata:
  name: postgres-backup
spec:
  schedule: "0 2 * * *"  # 2 AM daily
  jobTemplate:
    spec:
      template:
        spec:
          containers:
          - name: backup
            image: postgres:17
            command: ["/bin/sh", "-c"]
            args:
            - pg_dump -U postgres authdb | gzip > /backups/authdb-$(date +%Y%m%d).sql.gz
```

---

### 17. Database Connection Pooling Not Configured

**Severity:** Medium

**Issue:**
- No explicit connection pool configuration
- May exhaust database connections under load

**Recommended Fix:**
- Configure HikariCP settings in Spring Boot:
  ```yaml
  spring:
    datasource:
      hikari:
        maximum-pool-size: 20
        minimum-idle: 5
        connection-timeout: 30000
        idle-timeout: 600000
  ```

---

### 18. MongoDB Database Name Inconsistency

**Severity:** Low (Fixed but documented)
**Files:** `init-db/mongodb-seed.js`, `k8s/app-services.yaml`

**Issue:**
- Seed script used `monitoringdb`
- Initial K8s config used `metrics`
- Created confusion during deployment

**Resolution:**
- Standardized on `monitoringdb`
- Updated K8s configuration

**Lesson Learned:**
- Document database names in central location
- Validate configuration against seed scripts in CI/CD

---

## Observability & Monitoring

### 19. No Centralized Logging

**Severity:** High

**Issue:**
- Logs scattered across pods
- Must use `kubectl logs` to view individual service logs
- No log aggregation or searching capability

**Recommended Fix:**
- Deploy EFK stack (Elasticsearch, Fluentd, Kibana)
- Or use Loki + Grafana for lightweight alternative
- Configure structured logging (JSON format)
- Add correlation IDs for request tracing

---

### 20. No Distributed Tracing

**Severity:** Medium

**Issue:**
- Cannot trace requests across microservices
- Difficult to debug performance issues
- No visibility into service dependencies

**Recommended Fix:**
- Implement Spring Cloud Sleuth + Zipkin (already in docker-compose)
- Enable tracing in Kubernetes deployment
- Add trace IDs to logs
- Create service dependency graph

---

### 21. No Metrics Collection in Kubernetes

**Severity:** Medium

**Issue:**
- Prometheus/Grafana only configured for Docker Compose
- No application metrics in Kubernetes
- Cannot monitor CPU, memory, request rates

**Recommended Fix:**
- Deploy Prometheus Operator
- Configure ServiceMonitors for all services
- Import existing Grafana dashboards
- Set up alerting rules

---

### 22. No Error Tracking

**Severity:** Medium

**Issue:**
- No centralized error tracking
- Exceptions only visible in logs
- No automatic error grouping or notifications

**Recommended Fix:**
- Integrate Sentry or similar error tracking service
- Add error fingerprinting
- Configure Slack/email notifications for critical errors
- Track error rates in dashboards

---

## Frontend

### 23. API Base URL Hardcoded

**Severity:** Medium
**Files:** Frontend service configuration

**Issue:**
- API URL likely hardcoded or requires build-time configuration
- Cannot change backend URL without rebuilding frontend

**Recommended Fix:**
- Use environment variables injected at container startup
- Implement runtime configuration via `window._env_` object
- Support multiple backend environments

---

### 24. No Frontend Error Boundary Telemetry

**Severity:** Low
**Files:** `frontend/src/components/ErrorBoundary.tsx`

**Issue:**
- Error boundary catches errors but doesn't report them
- No visibility into frontend crashes

**Recommended Fix:**
- Integrate with error tracking service (Sentry)
- Log errors to backend analytics endpoint
- Track error frequency and user impact

---

### 25. No Frontend Performance Monitoring

**Severity:** Low

**Issue:**
- No visibility into frontend performance
- No metrics on page load time, API latency, render performance

**Recommended Fix:**
- Implement Web Vitals tracking (LCP, FID, CLS)
- Use Performance API for custom metrics
- Add RUM (Real User Monitoring)

---

## DevOps & Operations

### 26. No CI/CD Pipeline

**Severity:** High

**Issue:**
- Manual build and deployment process
- No automated testing in pipeline
- No deployment automation

**Recommended Fix:**
- Set up GitHub Actions or GitLab CI
- Automate: lint → test → build → push images → deploy to staging
- Implement automated rollback on failure
- Add deployment approvals for production

---

### 27. Zombie Container Prevention Scripts

**Severity:** Low (Mitigated)
**Files:** `scripts/cleanup-zombies.sh`, `scripts/safe-restart.sh`

**Issue:**
- Docker Compose can leave zombie containers on port conflicts
- Created prevention scripts, but root cause is Docker Compose limitation

**Current Mitigation:**
- `cleanup-zombies.sh` kills processes on all 18 ports
- `safe-restart.sh` validates clean state before startup
- Makefile integration for easy use

**Recommended Fix:**
- Migrate to Kubernetes for production (already done)
- Keep scripts for local development only
- Document that Kubernetes doesn't have this issue

---

### 28. No Environment Separation

**Severity:** Medium

**Issue:**
- Same configuration for dev, staging, production
- No environment-specific secrets or settings

**Recommended Fix:**
- Create separate namespaces: `basestation-dev`, `basestation-staging`, `basestation-prod`
- Use Kustomize or Helm for environment-specific configurations
- Implement separate secret stores per environment

---

### 29. No Disaster Recovery Plan

**Severity:** High

**Issue:**
- No documented DR procedures
- No tested recovery process
- No backup retention policy

**Recommended Fix:**
- Document disaster recovery procedures
- Define RTO and RPO targets
- Test recovery quarterly
- Create runbooks for common failure scenarios

---

### 30. Service Port Mismatches (Resolved)

**Severity:** High (Fixed but documented)
**Files:** `k8s/app-services.yaml`

**Issue:**
- Initial K8s deployment had port mismatches:
  - auth-service: running 8084, exposed 8085
  - base-station-service: running 8081, exposed 8084
  - monitoring-service: running 8082, exposed 8086
  - notification-service: running 8083, exposed 8087

**Resolution:**
- Updated all K8s service definitions to match actual application ports
- Updated gateway routes with correct ports

**Lesson Learned:**
- Validate ports between application.yml and K8s manifests
- Add port verification to deployment checklist
- Consider centralized port registry documentation

---

## Summary Statistics

**Total Issues:** 30

**By Severity:**
- Critical: 1 (No database backups)
- High: 6 (Manual DB init, no health probes, emptyDir volumes, hardcoded credentials, secrets in git, no centralized logging)
- Medium: 17
- Low: 6

**By Category:**
- Kubernetes: 7 issues
- Security: 5 issues
- Database: 3 issues
- Observability: 4 issues
- Architecture: 4 issues
- DevOps: 4 issues
- Frontend: 3 issues

---

## Prioritization Recommendations

### Phase 1: Production Readiness (Critical)
1. Implement database backups (#16)
2. Replace emptyDir with PersistentVolumes (#10)
3. Add health checks and readiness probes (#8)
4. Automate database initialization (#2)
5. Implement external secret management (#3)
6. Set up CI/CD pipeline (#26)

### Phase 2: Reliability & Observability (High)
7. Add centralized logging (EFK or Loki) (#19)
8. Pin all image versions (#7)
9. Configure resource limits appropriately (#9)
10. Implement circuit breakers (#12)
11. Set up Prometheus/Grafana in K8s (#21)

### Phase 3: Security Hardening (Medium)
12. Rotate JWT secrets (#4)
13. Restrict CORS configuration (#5)
14. Implement credential rotation (#1)
15. Add error tracking (Sentry) (#22)

### Phase 4: Optimization & Cleanup (Low)
16. Remove Eureka from K8s (#6)
17. Review database technology choices (#13)
18. Add distributed tracing (#20)
19. Implement rolling update strategies (#11)

---

## Related Documentation

- [README.md](README.md) - Project overview
- [QUICK_START.md](QUICK_START.md) - Docker Compose setup
- [KUBERNETES_DEPLOYMENT.md](KUBERNETES_DEPLOYMENT.md) - Kubernetes deployment guide
- [Makefile](Makefile) - Automation commands
