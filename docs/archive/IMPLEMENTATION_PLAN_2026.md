# Base Station Platform - Implementation Plan 2026

**Created:** 2026-01-06
**Status:** Planning Phase
**Priority:** Remediate Critical Issues → Cleanup & Optimize → Enhance Features

This plan addresses technical debt remediation, code cleanup, optimization, and project restructuring based on comprehensive audit results.

---

## Table of Contents

- [Phase 0: Cleanup & Restructure](#phase-0-cleanup--restructure)
- [Phase 1: Critical Production Readiness](#phase-1-critical-production-readiness)
- [Phase 2: Reliability & Observability](#phase-2-reliability--observability)
- [Phase 3: Security Hardening](#phase-3-security-hardening)
- [Phase 4: Optimization & Polish](#phase-4-optimization--polish)
- [Resource Estimates](#resource-estimates)
- [Success Metrics](#success-metrics)

---

## Phase 0: Cleanup & Restructure
**Goal:** Remove unused code, consolidate duplicates, optimize structure
**Duration:** 2-3 days
**Risk:** Low (mostly file moves and deletions)

### 0.1 Delete Unused Files & Components

#### Frontend Cleanup
- [ ] **Delete unused components**
  - `frontend/src/components/PulsingStatus.tsx` (1.8KB) - Not imported anywhere
  - `frontend/src/components/GlassCard.tsx` (1.2KB) - Not imported anywhere
  - Keep AnimatedCounter.tsx (IS used in Dashboard)

#### Build Artifacts
- [ ] **Remove generated/temporary files**
  - `effective-pom.xml` (2.5MB) - Auto-generated, should never be committed
  - `frontend/src/pages/Dashboard.tsx.backup` (already in git status as deleted)
  - Clean up all `target/` directories (694MB total, regenerable)

#### Documentation Cleanup
- [ ] **Remove untracked analysis documents** (5,707 lines):
  - `DESIGN_ANALYSIS.md`
  - `FINAL_FRONTEND_IMPROVEMENTS.md`
  - `FIXES_COMPLETED.md`
  - `FRESH_FRONTEND_ANALYSIS.md`
  - `FRONTEND_ANALYSIS.md`
  - `IMPLEMENTATION_PLAN.md` (old version)
  - `REMAINING_IMPROVEMENTS.md`
  - `TEXT_COLOR_READABILITY_FIXES.md`

**Rationale:** These are design notes/work logs, not living documentation. Keep work history in git commits instead.

### 0.2 Consolidate Database Initialization

**Current State:**
- 4 separate seed scripts: auth-seed.sql, basestation-seed.sql, notification-seed.sql, mongodb-seed.js
- Must be run individually with separate kubectl exec commands

**Target State:**
- [ ] Create `init-db/postgres-unified-seed.sql` combining:
  - auth-seed.sql (users table)
  - basestation-seed.sql (26 base stations)
  - notification-seed.sql (18 notifications)
- [ ] Keep `mongodb-seed.js` separate (different DB technology)
- [ ] Create `init-db/k8s-init-all-databases.sh` wrapper script
- [ ] Update Makefile `k8s_init_db` target to use unified script
- [ ] Update `KUBERNETES_DEPLOYMENT.md` with simplified instructions

**Benefits:**
- Single command database initialization
- Reduced documentation complexity
- Fewer kubectl exec operations

### 0.3 Restructure Project Folders

**Current Root Directory (cluttered):**
```
/
├── api-gateway/
├── auth-service/
├── base-station-service/
├── eureka-server/
├── frontend/
├── helm/
├── init-db/
├── k8s/
├── monitoring-service/
├── notification-service/
├── scripts/
├── docker-compose.yml
├── docker-compose.optimized.yml
├── pom.xml
├── README.md
├── QUICK_START.md
├── KUBERNETES_DEPLOYMENT.md
├── TECH_DEBT.md
├── (8 untracked analysis .md files)
└── ... more files
```

**Proposed Reorganization:**
```
/
├── services/                    # All microservices
│   ├── api-gateway/
│   ├── auth-service/
│   ├── base-station-service/
│   ├── eureka-server/
│   ├── monitoring-service/
│   └── notification-service/
├── frontend/                    # React application
├── deployment/                  # All deployment configs
│   ├── docker/
│   │   ├── docker-compose.yml
│   │   └── docker-compose.optimized.yml (reference)
│   ├── kubernetes/
│   │   ├── namespace.yaml
│   │   ├── secrets.yaml
│   │   ├── databases.yaml
│   │   └── app-services.yaml
│   ├── helm/                    # Optional: keep or delete?
│   └── init-db/
│       ├── postgres-unified-seed.sql
│       ├── mongodb-seed.js
│       └── k8s-init-all-databases.sh
├── scripts/                     # Automation scripts
│   ├── cleanup-zombies.sh
│   ├── safe-restart.sh
│   └── validate-clean-state.sh
├── docs/                        # All documentation
│   ├── README.md (symlink to ../README.md)
│   ├── QUICK_START.md
│   ├── KUBERNETES_DEPLOYMENT.md
│   ├── TECH_DEBT.md
│   ├── IMPLEMENTATION_PLAN_2026.md
│   └── ARCHITECTURE.md (new)
├── .github/                     # CI/CD workflows
├── pom.xml                      # Parent POM
├── README.md                    # Main entry point
└── .gitignore
```

**Migration Tasks:**
- [ ] Move all microservices to `services/` directory
- [ ] Update parent pom.xml module paths
- [ ] Move k8s/, helm/, init-db/ to `deployment/`
- [ ] Move documentation to `docs/` (keep README.md at root)
- [ ] Update all relative paths in scripts
- [ ] Update CI/CD workflow paths (.github/workflows/ci.yml)
- [ ] Update Docker Compose service build paths
- [ ] Test builds after restructure

**Rollback Plan:** Git can revert all file moves if issues arise.

### 0.4 Optimize Dependencies

#### Frontend (package.json)
- [ ] Remove unused dependencies:
  - `jscpd` (3.5MB) - Copy-paste detector, not used in scripts
  - `@types/canvas-confetti` - No actual canvas-confetti usage found
- [ ] Run `npm audit fix` to update vulnerable packages
- [ ] Consider replacing heavyweight libraries with lighter alternatives

#### Backend (pom.xml)
- [ ] All current dependencies appear to be in use (audit passed)
- [ ] Consider removing Spring Boot DevTools from production builds
- [ ] Verify no test dependencies leak into runtime scope

### 0.5 Decide on Helm vs Raw Manifests

**Current State:**
- Helm charts exist in `helm/base-station-platform/` (42KB)
- K8s deployment uses raw manifests in `k8s/` directory
- Helm charts include features NOT in raw manifests (health probes, PVCs)

**Options:**

**Option A: Delete Helm, Keep Raw Manifests**
- Pros: Simpler, one deployment method
- Cons: Lose templating, environment management, health probes config

**Option B: Delete Raw Manifests, Use Helm**
- Pros: Better templating, environment overrides, health probes included
- Cons: Learning curve, more complexity

**Option C: Keep Both (Current State)**
- Pros: Flexibility
- Cons: Duplicate maintenance, confusion

**Recommendation:** **Option B - Migrate to Helm** because:
1. Helm templates already have health probes (which we need)
2. Helm supports PersistentVolumeClaims (which we need)
3. Environment-specific values files (dev/staging/prod)
4. We're already maintaining both, consolidate to better option

**Tasks if adopting Helm:**
- [ ] Copy environment variable configs from raw manifests to Helm values.yaml
- [ ] Test Helm deployment: `helm install base-station ./helm/base-station-platform`
- [ ] Update KUBERNETES_DEPLOYMENT.md for Helm
- [ ] Delete `k8s/` directory
- [ ] Update Makefile commands to use Helm

---

## Phase 1: Critical Production Readiness
**Goal:** Fix data loss risks and enable production deployment
**Duration:** 1-2 weeks
**Risk:** High (database changes, deployment changes)

### 1.1 Replace emptyDir with PersistentVolumes

**Current Issue:** All databases use `emptyDir: {}` - data lost on pod restart.

**Implementation:**

#### Task 1.1.1: Create PersistentVolumeClaim Manifests
- [ ] Create `deployment/kubernetes/persistent-volumes.yaml`:
```yaml
---
# PostgreSQL Auth PVC
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: postgres-auth-pvc
  namespace: basestation-platform
spec:
  accessModes:
    - ReadWriteOnce
  resources:
    requests:
      storage: 5Gi
  storageClassName: local-path  # or standard, gp2, etc.
---
# PostgreSQL Base Station PVC
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: postgres-basestation-pvc
  namespace: basestation-platform
spec:
  accessModes:
    - ReadWriteOnce
  resources:
    requests:
      storage: 10Gi
  storageClassName: local-path
---
# PostgreSQL Notification PVC
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: postgres-notification-pvc
  namespace: basestation-platform
spec:
  accessModes:
    - ReadWriteOnce
  resources:
    requests:
      storage: 5Gi
  storageClassName: local-path
---
# MongoDB PVC
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: mongodb-pvc
  namespace: basestation-platform
spec:
  accessModes:
    - ReadWriteOnce
  resources:
    requests:
      storage: 20Gi  # Larger for time-series data
  storageClassName: local-path
---
# Redis PVC (optional - can use emptyDir as cache)
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: redis-pvc
  namespace: basestation-platform
spec:
  accessModes:
    - ReadWriteOnce
  resources:
    requests:
      storage: 2Gi
  storageClassName: local-path
---
# RabbitMQ PVC
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: rabbitmq-pvc
  namespace: basestation-platform
spec:
  accessModes:
    - ReadWriteOnce
  resources:
    requests:
      storage: 5Gi
  storageClassName: local-path
```

#### Task 1.1.2: Update Database Deployments
- [ ] Edit `deployment/kubernetes/databases.yaml`:
  - Replace all `emptyDir: {}` with `persistentVolumeClaim: { claimName: <pvc-name> }`
  - Update documentation references

#### Task 1.1.3: Test & Verify Persistence
- [ ] Deploy PVCs: `kubectl apply -f deployment/kubernetes/persistent-volumes.yaml`
- [ ] Verify PVC creation: `kubectl get pvc -n basestation-platform`
- [ ] Deploy databases: `kubectl apply -f deployment/kubernetes/databases.yaml`
- [ ] Seed databases
- [ ] Delete a database pod: `kubectl delete pod -n basestation-platform postgres-auth-<hash>`
- [ ] Verify data persists after pod recreation
- [ ] Document rollback procedure

**Storage Requirements:**
- Total: 47Gi minimum
- Production: 100Gi+ recommended with expansion room

### 1.2 Implement Database Backup Strategy

#### Task 1.2.1: Create Backup Scripts
- [ ] Create `deployment/init-db/backup-postgres.sh`:
```bash
#!/bin/bash
# Backup all PostgreSQL databases
DATE=$(date +%Y%m%d_%H%M%S)
BACKUP_DIR="/backups"

databases=("authdb" "basestationdb" "notificationdb")
pods=("postgres-auth" "postgres-basestation" "postgres-notification")

for i in "${!databases[@]}"; do
  db="${databases[$i]}"
  pod="${pods[$i]}"

  kubectl exec -n basestation-platform deployment/$pod -- \
    pg_dump -U postgres $db | gzip > "$BACKUP_DIR/${db}_${DATE}.sql.gz"

  echo "Backed up $db to ${db}_${DATE}.sql.gz"
done
```

- [ ] Create `deployment/init-db/backup-mongodb.sh`:
```bash
#!/bin/bash
# Backup MongoDB
DATE=$(date +%Y%m%d_%H%M%S)
BACKUP_DIR="/backups"

kubectl exec -n basestation-platform deployment/mongodb -- \
  mongodump --authenticationDatabase admin \
  -u admin -p <password> \
  --db monitoringdb \
  --archive | gzip > "$BACKUP_DIR/monitoringdb_${DATE}.archive.gz"

echo "Backed up monitoringdb to monitoringdb_${DATE}.archive.gz"
```

#### Task 1.2.2: Create Kubernetes CronJob for Automated Backups
- [ ] Create `deployment/kubernetes/backup-cronjobs.yaml`:
```yaml
---
apiVersion: batch/v1
kind: CronJob
metadata:
  name: postgres-backup
  namespace: basestation-platform
spec:
  schedule: "0 2 * * *"  # 2 AM daily
  successfulJobsHistoryLimit: 7
  failedJobsHistoryLimit: 3
  jobTemplate:
    spec:
      template:
        spec:
          containers:
          - name: backup
            image: postgres:17-alpine
            command: ["/bin/sh", "-c"]
            args:
            - |
              pg_dump -h postgres-auth -U postgres authdb | \
              gzip > /backups/authdb-$(date +%Y%m%d).sql.gz

              pg_dump -h postgres-basestation -U postgres basestationdb | \
              gzip > /backups/basestation-$(date +%Y%m%d).sql.gz

              pg_dump -h postgres-notification -U postgres notificationdb | \
              gzip > /backups/notification-$(date +%Y%m%d).sql.gz
            env:
            - name: PGPASSWORD
              valueFrom:
                secretKeyRef:
                  name: postgres-secrets
                  key: auth-password
            volumeMounts:
            - name: backup-storage
              mountPath: /backups
          volumes:
          - name: backup-storage
            persistentVolumeClaim:
              claimName: backup-pvc
          restartPolicy: OnFailure
---
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: backup-pvc
  namespace: basestation-platform
spec:
  accessModes:
    - ReadWriteOnce
  resources:
    requests:
      storage: 50Gi
  storageClassName: local-path
```

#### Task 1.2.3: Document Backup & Restore Procedures
- [ ] Add to `docs/KUBERNETES_DEPLOYMENT.md`:
  - How to trigger manual backup
  - How to restore from backup
  - Retention policy (keep 30 daily, 12 weekly, 12 monthly)
  - RPO: 24 hours (daily backups)
  - RTO: 1 hour (time to restore)

### 1.3 Add Kubernetes Health Probes

**Current State:** K8s manifests lack health probes (only Helm templates have them)

#### Task 1.3.1: Add Probes to All Services
- [ ] Update `deployment/kubernetes/app-services.yaml` for each service:

```yaml
# Example: Auth Service
spec:
  template:
    spec:
      containers:
      - name: auth-service
        # ... existing config ...
        livenessProbe:
          httpGet:
            path: /actuator/health
            port: 8084
          initialDelaySeconds: 60
          periodSeconds: 10
          timeoutSeconds: 5
          failureThreshold: 3
        readinessProbe:
          httpGet:
            path: /actuator/health/readiness
            port: 8084
          initialDelaySeconds: 30
          periodSeconds: 5
          timeoutSeconds: 3
          failureThreshold: 3
        startupProbe:
          httpGet:
            path: /actuator/health
            port: 8084
          initialDelaySeconds: 0
          periodSeconds: 10
          timeoutSeconds: 3
          failureThreshold: 30  # 5 minutes max startup time
```

Services to update:
- [ ] eureka-server (port 8762)
- [ ] auth-service (port 8084)
- [ ] base-station-service (port 8081)
- [ ] monitoring-service (port 8082)
- [ ] notification-service (port 8083)
- [ ] api-gateway (port 8080)

#### Task 1.3.2: Add Database Health Checks
- [ ] PostgreSQL instances: TCP socket probe on port 5432
- [ ] MongoDB: TCP socket probe on port 27017
- [ ] Redis: TCP socket probe on port 6379
- [ ] RabbitMQ: HTTP probe on port 15672 (management UI)

#### Task 1.3.3: Verify Probes Work
- [ ] Deploy updated manifests
- [ ] Monitor pod status during rollout
- [ ] Simulate failure (kill Java process inside pod)
- [ ] Verify Kubernetes restarts unhealthy pods
- [ ] Check that traffic is not routed to non-ready pods

### 1.4 Automate Database Initialization

**Current Issue:** Manual kubectl exec required to seed databases

#### Task 1.4.1: Create Init Containers for PostgreSQL
- [ ] Update `deployment/kubernetes/databases.yaml`:

```yaml
# Example: postgres-auth deployment
spec:
  template:
    spec:
      initContainers:
      - name: wait-for-postgres
        image: postgres:17-alpine
        command: ['sh', '-c', 'until pg_isready -h localhost -p 5432; do sleep 2; done']
      - name: seed-database
        image: postgres:17-alpine
        command: ['/bin/sh', '-c']
        args:
        - |
          if [ ! -f /var/lib/postgresql/data/.initialized ]; then
            psql -U postgres -d authdb < /docker-entrypoint-initdb.d/auth-seed.sql
            touch /var/lib/postgresql/data/.initialized
          fi
        env:
        - name: PGPASSWORD
          valueFrom:
            secretKeyRef:
              name: postgres-secrets
              key: auth-password
        volumeMounts:
        - name: init-scripts
          mountPath: /docker-entrypoint-initdb.d
        - name: postgres-storage
          mountPath: /var/lib/postgresql/data
      volumes:
      - name: init-scripts
        configMap:
          name: postgres-init-scripts
```

#### Task 1.4.2: Create ConfigMaps for Seed Scripts
- [ ] Create `deployment/kubernetes/init-configmaps.yaml`:
```yaml
---
apiVersion: v1
kind: ConfigMap
metadata:
  name: postgres-init-scripts
  namespace: basestation-platform
data:
  unified-seed.sql: |
    -- Auth database seed
    INSERT INTO users (username, password_hash, role, enabled)
    VALUES ('admin', '$2a$10$...', 'ROLE_ADMIN', true);

    -- Base station seed (26 stations)
    -- ... content from basestation-seed.sql ...

    -- Notification seed (18 notifications)
    -- ... content from notification-seed.sql ...
---
apiVersion: v1
kind: ConfigMap
metadata:
  name: mongodb-init-scripts
  namespace: basestation-platform
data:
  mongodb-seed.js: |
    // ... content from mongodb-seed.js ...
```

#### Task 1.4.3: Test Automated Initialization
- [ ] Delete namespace: `kubectl delete namespace basestation-platform`
- [ ] Recreate: `kubectl apply -f deployment/kubernetes/`
- [ ] Verify databases are seeded automatically
- [ ] Test login works immediately
- [ ] Test metrics data exists

### 1.5 Enable CI/CD Docker Build & Push

**Current Issue:** Docker build job disabled in `.github/workflows/ci.yml` (line 127: `if: false`)

#### Task 1.5.1: Enable Docker Build Job
- [ ] Edit `.github/workflows/ci.yml`:
  - Remove `if: false` from docker job
  - Configure GitHub Container Registry (GHCR) secrets
  - Add image tagging with git SHA and semantic version

#### Task 1.5.2: Add Deployment Job
- [ ] Create `.github/workflows/deploy.yml`:
```yaml
name: Deploy to Kubernetes

on:
  workflow_run:
    workflows: ["CI"]
    types: [completed]
    branches: [master]

jobs:
  deploy-staging:
    if: ${{ github.event.workflow_run.conclusion == 'success' }}
    runs-on: ubuntu-latest
    steps:
    - uses: actions/checkout@v4

    - name: Set up kubectl
      uses: azure/setup-kubectl@v3

    - name: Configure kubeconfig
      run: |
        mkdir -p $HOME/.kube
        echo "${{ secrets.KUBECONFIG }}" > $HOME/.kube/config

    - name: Deploy to staging
      run: |
        kubectl set image deployment/auth-service \
          auth-service=ghcr.io/${{ github.repository }}/auth-service:${{ github.sha }} \
          -n basestation-staging
        # Repeat for all services

    - name: Verify deployment
      run: kubectl rollout status deployment/auth-service -n basestation-staging
```

#### Task 1.5.3: Configure Secrets
- [ ] Add to GitHub repository secrets:
  - `KUBECONFIG` - Base64 encoded kubeconfig file
  - `GHCR_TOKEN` - GitHub personal access token for container registry

---

## Phase 2: Reliability & Observability
**Goal:** Enable monitoring, logging, and distributed tracing
**Duration:** 1-2 weeks
**Risk:** Medium (new infrastructure)

### 2.1 Deploy Prometheus & Grafana to Kubernetes

**Current State:** Configured in code but not deployed

#### Task 2.1.1: Create Monitoring Stack Manifests
- [ ] Create `deployment/kubernetes/monitoring-stack.yaml`:
```yaml
---
# Prometheus Deployment
apiVersion: apps/v1
kind: Deployment
metadata:
  name: prometheus
  namespace: basestation-platform
spec:
  replicas: 1
  selector:
    matchLabels:
      app: prometheus
  template:
    metadata:
      labels:
        app: prometheus
    spec:
      containers:
      - name: prometheus
        image: prom/prometheus:v2.48.0
        ports:
        - containerPort: 9090
        volumeMounts:
        - name: config
          mountPath: /etc/prometheus
        - name: data
          mountPath: /prometheus
      volumes:
      - name: config
        configMap:
          name: prometheus-config
      - name: data
        persistentVolumeClaim:
          claimName: prometheus-pvc
---
# Prometheus Service
apiVersion: v1
kind: Service
metadata:
  name: prometheus
  namespace: basestation-platform
spec:
  type: NodePort
  selector:
    app: prometheus
  ports:
  - port: 9090
    targetPort: 9090
    nodePort: 30090
---
# Grafana Deployment
apiVersion: apps/v1
kind: Deployment
metadata:
  name: grafana
  namespace: basestation-platform
spec:
  replicas: 1
  selector:
    matchLabels:
      app: grafana
  template:
    metadata:
      labels:
        app: grafana
    spec:
      containers:
      - name: grafana
        image: grafana/grafana:10.2.2
        ports:
        - containerPort: 3000
        env:
        - name: GF_SECURITY_ADMIN_PASSWORD
          valueFrom:
            secretKeyRef:
              name: grafana-secret
              key: password
        volumeMounts:
        - name: data
          mountPath: /var/lib/grafana
      volumes:
      - name: data
        persistentVolumeClaim:
          claimName: grafana-pvc
---
# Grafana Service
apiVersion: v1
kind: Service
metadata:
  name: grafana
  namespace: basestation-platform
spec:
  type: NodePort
  selector:
    app: grafana
  ports:
  - port: 3000
    targetPort: 3000
    nodePort: 30300
```

#### Task 2.1.2: Configure Prometheus Scraping
- [ ] Create `deployment/kubernetes/prometheus-configmap.yaml`:
```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: prometheus-config
  namespace: basestation-platform
data:
  prometheus.yml: |
    global:
      scrape_interval: 15s

    scrape_configs:
    - job_name: 'kubernetes-pods'
      kubernetes_sd_configs:
      - role: pod
      relabel_configs:
      - source_labels: [__meta_kubernetes_pod_annotation_prometheus_io_scrape]
        action: keep
        regex: true
      - source_labels: [__meta_kubernetes_pod_annotation_prometheus_io_path]
        action: replace
        target_label: __metrics_path__
        regex: (.+)
      - source_labels: [__address__, __meta_kubernetes_pod_annotation_prometheus_io_port]
        action: replace
        regex: ([^:]+)(?::\d+)?;(\d+)
        replacement: $1:$2
        target_label: __address__
```

#### Task 2.1.3: Add Prometheus Annotations to Services
- [ ] Update all service deployments with annotations:
```yaml
metadata:
  annotations:
    prometheus.io/scrape: "true"
    prometheus.io/path: "/actuator/prometheus"
    prometheus.io/port: "8084"
```

#### Task 2.1.4: Import Grafana Dashboards
- [ ] Create dashboard ConfigMaps
- [ ] Import Spring Boot dashboard (ID: 4701)
- [ ] Import JVM dashboard (ID: 4701)
- [ ] Create custom dashboard for base station metrics

### 2.2 Deploy Zipkin for Distributed Tracing

#### Task 2.2.1: Deploy Zipkin Server
- [ ] Add to `deployment/kubernetes/monitoring-stack.yaml`:
```yaml
---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: zipkin
  namespace: basestation-platform
spec:
  replicas: 1
  selector:
    matchLabels:
      app: zipkin
  template:
    metadata:
      labels:
        app: zipkin
    spec:
      containers:
      - name: zipkin
        image: openzipkin/zipkin:2.24
        ports:
        - containerPort: 9411
---
apiVersion: v1
kind: Service
metadata:
  name: zipkin
  namespace: basestation-platform
spec:
  type: NodePort
  selector:
    app: zipkin
  ports:
  - port: 9411
    targetPort: 9411
    nodePort: 30411
```

#### Task 2.2.2: Update Service Configurations
- [ ] Verify all services have Zipkin endpoint configured (already done)
- [ ] Enable tracing in K8s environment variables:
```yaml
- name: MANAGEMENT_ZIPKIN_TRACING_ENDPOINT
  value: http://zipkin:9411/api/v2/spans
- name: MANAGEMENT_TRACING_SAMPLING_PROBABILITY
  value: "1.0"
```

### 2.3 Implement Centralized Logging

**Option A: ELK Stack (Elasticsearch, Logstash, Kibana)**
**Option B: Loki + Promtail + Grafana (Lighter)**

Recommend **Option B** for resource efficiency.

#### Task 2.3.1: Deploy Loki
- [ ] Create `deployment/kubernetes/logging-stack.yaml`:
```yaml
---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: loki
  namespace: basestation-platform
spec:
  replicas: 1
  selector:
    matchLabels:
      app: loki
  template:
    metadata:
      labels:
        app: loki
    spec:
      containers:
      - name: loki
        image: grafana/loki:2.9.3
        ports:
        - containerPort: 3100
        volumeMounts:
        - name: config
          mountPath: /etc/loki
        - name: data
          mountPath: /loki
      volumes:
      - name: config
        configMap:
          name: loki-config
      - name: data
        persistentVolumeClaim:
          claimName: loki-pvc
---
apiVersion: v1
kind: Service
metadata:
  name: loki
  namespace: basestation-platform
spec:
  selector:
    app: loki
  ports:
  - port: 3100
    targetPort: 3100
```

#### Task 2.3.2: Deploy Promtail (Log Collector)
- [ ] Deploy Promtail as DaemonSet (runs on every node)
- [ ] Configure to scrape all pod logs
- [ ] Forward to Loki

#### Task 2.3.3: Configure Grafana Data Source
- [ ] Add Loki as data source in Grafana
- [ ] Create log exploration dashboards
- [ ] Set up log-based alerts

### 2.4 Tune Resource Limits Based on Actual Usage

#### Task 2.4.1: Profile Services Under Load
- [ ] Deploy metrics collection
- [ ] Run load tests simulating production traffic
- [ ] Monitor CPU, memory, network for 7 days
- [ ] Identify resource bottlenecks

#### Task 2.4.2: Adjust Resource Specifications
- [ ] Update based on profiling data
- [ ] Set realistic limits (prevent OOMKills)
- [ ] Set appropriate requests (efficient scheduling)
- [ ] Configure HPA (Horizontal Pod Autoscaler) for high-traffic services

---

## Phase 3: Security Hardening
**Goal:** Improve secrets management, implement rotation, add network policies
**Duration:** 1 week
**Risk:** Medium (security changes require careful testing)

### 3.1 Implement Secret Rotation Mechanism

#### Task 3.1.1: JWT Secret Rotation
- [ ] Implement multi-key JWT validation (support old + new keys during rotation)
- [ ] Create rotation script
- [ ] Document rotation procedure
- [ ] Schedule quarterly rotation

#### Task 3.1.2: Database Password Rotation
- [ ] Create rotation script that:
  - Generates new passwords
  - Updates K8s secrets
  - Restarts services
  - Verifies connectivity
- [ ] Test in staging environment
- [ ] Document procedure

### 3.2 External Secret Management (Optional)

**Options:**
- HashiCorp Vault
- AWS Secrets Manager
- Azure Key Vault
- Sealed Secrets (K8s-native)

**Recommendation:** Sealed Secrets (lowest complexity)

#### Task 3.2.1: Deploy Sealed Secrets Controller
- [ ] Install Sealed Secrets CRD
- [ ] Deploy controller to cluster
- [ ] Install kubeseal CLI locally

#### Task 3.2.2: Convert Secrets to SealedSecrets
- [ ] Encrypt all secrets with kubeseal
- [ ] Replace Secret manifests with SealedSecret manifests
- [ ] Safe to commit encrypted secrets to git

### 3.3 Restrict CORS Configuration

#### Task 3.3.1: Environment-Specific CORS
- [ ] Create separate CORS configurations:
  - Dev: `http://localhost:3000,http://localhost:30000`
  - Staging: `https://staging.example.com`
  - Production: `https://app.example.com`
- [ ] Use Helm values or ConfigMaps for environment differentiation

### 3.4 Add Kubernetes Network Policies

#### Task 3.4.1: Define Network Policies
- [ ] Create `deployment/kubernetes/network-policies.yaml`:
```yaml
---
# API Gateway can only receive traffic from frontend
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: api-gateway-ingress
  namespace: basestation-platform
spec:
  podSelector:
    matchLabels:
      app: api-gateway
  policyTypes:
  - Ingress
  ingress:
  - from:
    - podSelector:
        matchLabels:
          app: frontend
    - podSelector: {}  # Allow from other services
    ports:
    - protocol: TCP
      port: 8080
---
# Databases can only be accessed by their respective services
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: postgres-auth-ingress
  namespace: basestation-platform
spec:
  podSelector:
    matchLabels:
      app: postgres-auth
  policyTypes:
  - Ingress
  ingress:
  - from:
    - podSelector:
        matchLabels:
          app: auth-service
    ports:
    - protocol: TCP
      port: 5432
```

#### Task 3.4.2: Test Network Policies
- [ ] Deploy policies
- [ ] Verify authorized connections work
- [ ] Verify unauthorized connections are blocked
- [ ] Test service-to-service communication

---

## Phase 4: Optimization & Polish
**Goal:** Final optimizations, documentation, developer experience
**Duration:** 1 week
**Risk:** Low

### 4.1 Database Optimization

#### Task 4.1.1: Configure HikariCP Connection Pools
- [ ] Add to all service `application.yml`:
```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      connection-timeout: 30000
      idle-timeout: 600000
      max-lifetime: 1800000
      connection-test-query: SELECT 1
```

#### Task 4.1.2: Implement Database Migrations
- [ ] Replace Hibernate auto-DDL with Flyway
- [ ] Add Flyway dependency to pom.xml
- [ ] Create migration scripts in `src/main/resources/db/migration/`
- [ ] Test migration on fresh database
- [ ] Set `spring.jpa.hibernate.ddl-auto: validate` (production safe)

### 4.2 Frontend Optimization

#### Task 4.2.1: Bundle Size Optimization
- [ ] Analyze bundle with `npm run build -- --stats`
- [ ] Implement code splitting for routes
- [ ] Lazy load heavy components
- [ ] Tree-shake unused dependencies

#### Task 4.2.2: Implement Error Tracking
- [ ] Add Sentry SDK to frontend
- [ ] Configure source maps for error tracking
- [ ] Add error boundaries with Sentry reporting
- [ ] Set up error alerts

### 4.3 Remove Eureka from Kubernetes

#### Task 4.3.1: Clean Up K8s Manifests
- [ ] Remove eureka-server deployment from `deployment/kubernetes/app-services.yaml`
- [ ] Remove eureka-server service
- [ ] Verify all services have `EUREKA_CLIENT_ENABLED: "false"`
- [ ] Test deployment without Eureka

#### Task 4.3.2: Keep Eureka for Docker Compose
- [ ] Verify Eureka still works in docker-compose.yml
- [ ] Document: "Eureka for local dev only, K8s uses native DNS"

### 4.4 Implement Rolling Update Strategy

#### Task 4.4.1: Configure Deployment Strategies
- [ ] Add to all service deployments:
```yaml
spec:
  replicas: 2  # Minimum for zero-downtime
  strategy:
    type: RollingUpdate
    rollingUpdate:
      maxUnavailable: 0  # Always keep at least 1 running
      maxSurge: 1        # Create new before terminating old
```

### 4.5 Documentation Updates

#### Task 4.5.1: Create Architecture Documentation
- [ ] Create `docs/ARCHITECTURE.md`:
  - System architecture diagram
  - Service dependency graph
  - Database schema overview
  - API contract documentation
  - Deployment architecture

#### Task 4.5.2: Update All Documentation
- [ ] `README.md` - Project overview, quick links
- [ ] `docs/QUICK_START.md` - Updated for new folder structure
- [ ] `docs/KUBERNETES_DEPLOYMENT.md` - Updated with Helm, PVCs, backups
- [ ] `docs/TECH_DEBT.md` - Mark completed items
- [ ] `docs/CONTRIBUTING.md` - Developer onboarding guide

#### Task 4.5.3: Create Runbooks
- [ ] `docs/runbooks/INCIDENT_RESPONSE.md`
- [ ] `docs/runbooks/DATABASE_RESTORE.md`
- [ ] `docs/runbooks/SECRET_ROTATION.md`
- [ ] `docs/runbooks/SCALING.md`

---

## Resource Estimates

### Development Time

| Phase | Duration | Team Size | Total Dev Days |
|-------|----------|-----------|----------------|
| Phase 0: Cleanup | 2-3 days | 1 dev | 3 days |
| Phase 1: Production Readiness | 1-2 weeks | 2 devs | 20 days |
| Phase 2: Observability | 1-2 weeks | 2 devs | 20 days |
| Phase 3: Security | 1 week | 1 dev | 5 days |
| Phase 4: Optimization | 1 week | 1 dev | 5 days |
| **Total** | **5-7 weeks** | **2 devs avg** | **53 days** |

### Infrastructure Resources

**Current (Dev/Local):**
- CPU: ~10 cores
- Memory: ~15Gi
- Storage: ~50Gi (ephemeral)

**Target (Production with Monitoring):**
- CPU: ~15 cores (50% increase for monitoring stack)
- Memory: ~25Gi (66% increase for Prometheus, Loki)
- Storage: ~200Gi (persistent + backups)

**Cost Estimate (AWS EKS):**
- 3x m5.xlarge nodes (4 vCPU, 16GB each): ~$350/month
- 200GB EBS storage: ~$20/month
- Load balancer: ~$20/month
- **Total: ~$390/month** (before data transfer, backups to S3)

---

## Success Metrics

### Phase 0 (Cleanup)
- [ ] Zero untracked documentation files
- [ ] Zero unused frontend components
- [ ] <50 root-level files/folders
- [ ] Build time reduced by >10%

### Phase 1 (Production Readiness)
- [ ] 100% data persistence (no emptyDir)
- [ ] Automated daily backups running
- [ ] Zero manual steps in deployment
- [ ] All pods pass health checks
- [ ] CI/CD deploying to staging automatically

### Phase 2 (Observability)
- [ ] All metrics visible in Grafana
- [ ] Distributed traces viewable in Zipkin
- [ ] Centralized logs searchable
- [ ] <5 minute MTTR (Mean Time To Resolve) for common issues

### Phase 3 (Security)
- [ ] Zero hardcoded secrets in code
- [ ] Network policies enforcing least privilege
- [ ] Quarterly secret rotation documented and tested
- [ ] Pass security audit

### Phase 4 (Optimization)
- [ ] Database migrations automated (Flyway)
- [ ] Frontend bundle size <500KB gzipped
- [ ] Zero-downtime deployments proven
- [ ] Developer onboarding time <2 hours

---

## Risk Mitigation

### High-Risk Changes

1. **Database Migration (emptyDir → PVC)**
   - Risk: Data loss during migration
   - Mitigation: Backup before migration, test in dev first, use blue-green deployment

2. **Folder Restructure**
   - Risk: Breaking builds, paths, imports
   - Mitigation: Git branch, test all builds before merge, document rollback

3. **CI/CD Automation**
   - Risk: Accidental production deployment
   - Mitigation: Deploy to staging first, require manual approval for prod

### Rollback Plans

- All changes in git - can revert commits
- Database backups before any schema changes
- K8s manifests can be rolled back: `kubectl rollout undo deployment/<name>`
- Keep old Docker images tagged for quick rollback

---

## Next Steps

1. **Review this plan** with team and stakeholders
2. **Prioritize phases** based on business needs
3. **Assign owners** to each task
4. **Create tracking board** (Jira, GitHub Projects, etc.)
5. **Schedule kickoff** for Phase 0
6. **Set up weekly sync** to track progress

---

## References

- [TECH_DEBT.md](TECH_DEBT.md) - Detailed technical debt analysis
- [KUBERNETES_DEPLOYMENT.md](KUBERNETES_DEPLOYMENT.md) - Current K8s deployment guide
- Audit reports from agent ID: a03868f (implementation status)
- Cleanup analysis from agent ID: ab4d71a (unused code)

**Last Updated:** 2026-01-06
**Next Review:** After Phase 0 completion
