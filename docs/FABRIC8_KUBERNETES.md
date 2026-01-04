# Fabric8 Kubernetes Deployment

This document explains how to use Eclipse JKube (Fabric8) to generate Kubernetes manifests for the Base Station Platform.

## Overview

We use Eclipse JKube Maven Plugin to automatically generate Kubernetes/OpenShift manifests from our Spring Boot applications. This eliminates manual YAML management and reduces configuration clutter.

## Setup

### Prerequisites
- Maven 3.6+
- Java 21
- Docker (for building images)
- Kubernetes cluster (minikube, kind, or cloud provider)

### Added Configuration

**Root POM** (`pom.xml`):
```xml
<properties>
    <fabric8.version>0.16.2</fabric8.version>
</properties>

<build>
    <pluginManagement>
        <plugins>
            <plugin>
                <groupId>org.eclipse.jkube</groupId>
                <artifactId>kubernetes-maven-plugin</artifactId>
                <version>${fabric8.version}</version>
            </plugin>
        </plugins>
    </pluginManagement>
</build>
```

## Usage

### Generate Kubernetes Manifests

Generate K8s YAML files for all services:
```bash
mvn k8s:resource
```

This creates manifests in `target/classes/META-INF/jkube/kubernetes/` for each service:
- `<service-name>-deployment.yml`
- `<service-name>-service.yml`
- `<service-name>-configmap.yml` (if needed)

### Build Docker Images

Build container images for all services:
```bash
mvn k8s:build
```

Images are tagged as: `<groupId>/<artifactId>:<version>`
Example: `com.huawei/base-station-service:1.0.0`

### Deploy to Kubernetes

Deploy all services to your Kubernetes cluster:
```bash
mvn k8s:apply
```

Or do everything in one step:
```bash
mvn clean package k8s:build k8s:resource k8s:apply
```

### Undeploy from Kubernetes

Remove all deployed resources:
```bash
mvn k8s:undeploy
```

## Configuration

### Service-Specific Configuration

Create `src/main/jkube/` directory in each service with custom fragments:

**Example: `base-station-service/src/main/jkube/deployment.yml`**
```yaml
spec:
  template:
    spec:
      containers:
        - env:
            - name: SPRING_PROFILES_ACTIVE
              value: kubernetes
            - name: SPRING_DATASOURCE_URL
              valueFrom:
                configMapKeyRef:
                  name: db-config
                  key: postgres-url
```

### Environment Variables

JKube automatically reads from `application.yml` and generates appropriate ConfigMaps/Secrets.

Use `src/main/jkube/configmap.yml` to override:
```yaml
data:
  application.properties: |
    spring.datasource.url=jdbc:postgresql://postgres:5432/db
    spring.datasource.username=postgres
```

## Database Deployment

For databases, we keep using `docker-compose.yml` for local development and create separate K8s manifests for production.

**Why not auto-generate database manifests?**
- Databases require StatefulSets, not Deployments
- PersistentVolumeClaims need manual provisioning
- Connection strings differ between environments

## Removing SQL Seed Scripts

We removed auto-injection of SQL seed scripts from `docker-compose.yml`:

**Before:**
```yaml
postgres-auth:
  volumes:
    - postgres-auth-data:/var/lib/postgresql/data
    - ./init-db/auth-seed.sql:/docker-entrypoint-initdb.d/seed.sql
```

**After:**
```yaml
postgres-auth:
  volumes:
    - postgres-auth-data:/var/lib/postgresql/data
```

**Why?**
- Production databases should NOT auto-seed on startup
- Manual data initialization is safer and more auditable
- Seed scripts remain in `init-db/` for manual execution

### Manual Data Initialization

After deploying fresh databases:

```bash
# PostgreSQL
docker exec postgres-auth psql -U postgres -d authdb -f /path/to/auth-seed.sql

# Or for Kubernetes:
kubectl exec -it postgres-auth-0 -- psql -U postgres -d authdb < init-db/auth-seed.sql

# MongoDB
docker exec mongodb mongosh --file /path/to/mongodb-seed.js

# Or for Kubernetes:
kubectl cp init-db/mongodb-seed.js mongodb-0:/tmp/
kubectl exec -it mongodb-0 -- mongosh --file /tmp/mongodb-seed.js
```

## Cleanup YML Files

We cleaned up all `application.yml` files by:

1. **Removing verbose comments** - Keep code self-documenting
2. **Removing redundant configuration** - Use Spring Boot defaults
3. **Consolidating related properties** - Group by functionality

**Example reduction:**
- **Before**: 116 lines with extensive comments
- **After**: 75 lines, clean and readable

## Benefits

### With Fabric8/JKube:
✅ **No manual YAML maintenance** - Generated from code
✅ **Consistent configuration** - Spring Boot properties → K8s manifests
✅ **Version controlled** - Changes tracked in application.yml
✅ **Less duplication** - One source of truth
✅ **Automatic health/readiness probes** - From Spring Actuator

### Without Fabric8 (manual YAMLs):
❌ **Drift between environments** - Dev vs Prod configs diverge
❌ **Copy-paste errors** - Repetitive YAML for each service
❌ **Forgotten updates** - Change port in code, forget YAML
❌ **Large YAML files** - 200+ line deployment manifests

## Migration Path

### Phase 1: Docker Compose (Current)
- Local development with `docker-compose.yml`
- Manual SQL seeding after deployment
- Cleaned up application.yml files

### Phase 2: Kubernetes (Next)
- Generate manifests with `mvn k8s:resource`
- Deploy to staging with `mvn k8s:apply`
- Use Helm for complex deployments

### Phase 3: Production
- CI/CD pipeline builds images
- ArgoCD/Flux GitOps deployment
- Separate configs per environment

## Common Commands

```bash
# Development workflow
mvn clean package                    # Build JARs
mvn k8s:build                       # Build Docker images
mvn k8s:resource                    # Generate K8s YAMLs
mvn k8s:apply                       # Deploy to K8s

# Debug generated manifests
ls target/classes/META-INF/jkube/kubernetes/

# Watch deployment
mvn k8s:log -Djkube.log.follow=true

# Port forward to service
mvn k8s:debug                       # Enable remote debugging
kubectl port-forward svc/base-station-service 8081:8081
```

## References

- [Eclipse JKube Documentation](https://www.eclipse.dev/jkube/docs/)
- [Spring Boot on Kubernetes](https://spring.io/guides/gs/spring-boot-kubernetes/)
- [Kubernetes Maven Plugin Guide](https://www.eclipse.dev/jkube/docs/kubernetes-maven-plugin)
