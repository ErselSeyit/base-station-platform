# Kubernetes Migration: Kill Eureka

## Why Eureka Doesn't Belong in Kubernetes

**Current Problem**: Running Eureka in K8s is redundant—K8s has native service discovery via DNS and Services.

**Analogy**: Using Eureka in K8s is like installing a GPS in a self-driving car that already has one.

### What Eureka Does:
- Service registration (services announce themselves)
- Service discovery (clients find service IPs)
- Health checking
- Load balancing

### What K8s Already Does:
- **Service registration**: Pod labels + Services automatically
- **Service discovery**: CoreDNS resolves `service-name.namespace.svc.cluster.local`
- **Health checking**: Liveness/readiness probes
- **Load balancing**: kube-proxy + iptables/IPVS

---

## Migration Path: Spring Cloud Kubernetes

### Step 1: Replace Eureka Client Dependency

**Before** (`pom.xml` in each service):
```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-netflix-eureka-client</artifactId>
</dependency>
```

**After**:
```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-kubernetes-client-all</artifactId>
</dependency>
```

### Step 2: Update application.yml

**Before** (Eureka-based):
```yaml
spring:
  application:
    name: base-station-service
eureka:
  client:
    service-url:
      defaultZone: http://eureka-server:8761/eureka/
```

**After** (K8s-native):
```yaml
spring:
  application:
    name: base-station-service
  cloud:
    kubernetes:
      discovery:
        enabled: true
        all-namespaces: false  # Only discover services in same namespace
      reload:
        enabled: true  # Reload config on ConfigMap/Secret changes
```

### Step 3: Update Gateway Routes

**Before** (load-balanced via Eureka):
```yaml
spring:
  cloud:
    gateway:
      routes:
        - id: base-station-service
          uri: lb://base-station-service  # Eureka service ID
```

**After** (K8s Service DNS):
```yaml
spring:
  cloud:
    gateway:
      routes:
        - id: base-station-service
          uri: http://base-station-service.default.svc.cluster.local:8084
          # Or use Spring Cloud Kubernetes discovery:
          # uri: lb://base-station-service  (still works with K8s discovery)
```

### Step 4: RBAC Permissions

Spring Cloud Kubernetes needs permissions to read K8s Services and Endpoints:

**`k8s/rbac.yaml`**:
```yaml
apiVersion: v1
kind: ServiceAccount
metadata:
  name: base-station-sa
  namespace: default

---
apiVersion: rbac.authorization.k8s.io/v1
kind: Role
metadata:
  name: service-discovery
  namespace: default
rules:
  - apiGroups: [""]
    resources: ["services", "endpoints", "pods"]
    verbs: ["get", "list", "watch"]
  - apiGroups: [""]
    resources: ["configmaps", "secrets"]
    verbs: ["get", "list", "watch"]

---
apiVersion: rbac.authorization.k8s.io/v1
kind:RoleBinding
metadata:
  name: service-discovery-binding
  namespace: default
roleRef:
  apiGroup: rbac.authorization.k8s.io
  kind: Role
  name: service-discovery
subjects:
  - kind: ServiceAccount
    name: base-station-sa
    namespace: default
```

### Step 5: Deployment with Service Account

**`k8s/base-station-deployment.yaml`**:
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: base-station-service
spec:
  replicas: 3
  selector:
    matchLabels:
      app: base-station-service
  template:
    metadata:
      labels:
        app: base-station-service
    spec:
      serviceAccountName: base-station-sa  # ← CRITICAL for discovery
      containers:
        - name: base-station-service
          image: base-station-service:latest
          ports:
            - containerPort: 8084
          env:
            - name: SPRING_PROFILES_ACTIVE
              value: kubernetes
          livenessProbe:
            httpGet:
              path: /actuator/health/liveness
              port: 8084
            initialDelaySeconds: 60
            periodSeconds: 10
          readinessProbe:
            httpGet:
              path: /actuator/health/readiness
              port: 8084
            initialDelaySeconds: 30
            periodSeconds: 5
---
apiVersion: v1
kind: Service
metadata:
  name: base-station-service
spec:
  selector:
    app: base-station-service
  ports:
    - protocol: TCP
      port: 8084
      targetPort: 8084
  type: ClusterIP
```

---

## Architecture Comparison

### Before (Eureka):
```
┌─────────┐      ┌──────────┐      ┌─────────────┐
│ Gateway │─────>│  Eureka  │<─────│ Base Station│
└─────────┘      │  Server  │      │   Service   │
                 └──────────┘      └─────────────┘
                      ↓
                 [Registry]
```

- Gateway queries Eureka for service IPs
- Services register themselves with Eureka heartbeats
- Extra hop for every discovery request

### After (K8s Native):
```
┌─────────┐                        ┌─────────────┐
│ Gateway │──────────────────────>│ Base Station│
└─────────┘                        │   Service   │
     │                             └─────────────┘
     │
     v
┌──────────┐
│ CoreDNS  │ (K8s built-in)
└──────────┘
```

- Gateway directly calls `base-station-service.default.svc.cluster.local`
- CoreDNS resolves to Pod IPs automatically
- kube-proxy load balances across healthy Pods
- No extra service required

---

## What Gets Removed

1. **eureka-server/** directory (entire service deleted)
2. **Eureka client dependencies** from all pom.xml files
3. **Eureka config** from all application.yml files
4. **Docker image** for eureka-server (saves ~200MB)
5. **docker-compose.yml** eureka-server entry

---

## Load Balancing Comparison

### Eureka (Client-Side Load Balancing):
```java
// Uses Netflix Ribbon to select instance
@LoadBalanced
@Bean
RestTemplate restTemplate() {
    return new RestTemplate();
}

// Calls lb://base-station-service, Ribbon picks an instance
restTemplate.getForObject("lb://base-station-service/api/stations", ...)
```

### K8s (Server-Side Load Balancing):
```java
// No special annotation needed
@Bean
RestTemplate restTemplate() {
    return new RestTemplate();
}

// Calls K8s Service, kube-proxy handles load balancing
restTemplate.getForObject(
    "http://base-station-service.default.svc.cluster.local:8084/api/stations",
    ...
)
```

**Or keep using Spring Cloud LoadBalancer with K8s discovery**:
```java
@LoadBalanced
@Bean
WebClient.Builder loadBalancedWebClientBuilder() {
    return WebClient.builder();
}

// Still works! K8s discovery populates the service registry
webClient.get()
    .uri("lb://base-station-service/api/stations")
    .retrieve()
```

---

## Health Checks: Eureka vs K8s

### Eureka:
```yaml
eureka:
  instance:
    lease-renewal-interval-in-seconds: 30
    lease-expiration-duration-in-seconds: 90
```
- Services send heartbeats every 30s
- Marked DOWN after 90s of no heartbeats
- Gateway continues routing to DOWN instances until evicted

### K8s:
```yaml
readinessProbe:
  httpGet:
    path: /actuator/health/readiness
    port: 8084
  periodSeconds: 5  # Check every 5 seconds
  failureThreshold: 3  # Fail after 3 consecutive failures (15s total)
```
- kube-proxy immediately stops routing to failed Pods
- Much faster failure detection (15s vs 90s)
- Automatic Pod restart via liveness probes

---

## Migration Checklist

- [ ] Add Spring Cloud Kubernetes dependencies to all services
- [ ] Remove Eureka client dependencies
- [ ] Create ServiceAccount and RBAC rules
- [ ] Update all application.yml files
- [ ] Create K8s Deployment + Service manifests for each microservice
- [ ] Update Gateway routes to use K8s Service DNS
- [ ] Add liveness/readiness probes
- [ ] Delete eureka-server/ directory
- [ ] Remove eureka-server from docker-compose.yml
- [ ] Test service discovery: `kubectl exec -it gateway-pod -- nslookup base-station-service`
- [ ] Verify load balancing: `kubectl logs gateway-pod | grep "Routing to"`

---

## Eureka vs Kubernetes Service Discovery

**Why Eureka exists in this project:**
- Demonstrates service discovery patterns for Docker Compose deployment
- Educational value for understanding Spring Cloud Netflix
- Works for local development without Kubernetes

**Why it's redundant in Kubernetes:**
- K8s Services provide native service discovery via CoreDNS
- kube-proxy handles load balancing automatically
- Readiness probes replace Eureka heartbeats
- Reduces infrastructure complexity and image size

**Migration benefits:**
- ~10 seconds faster startup per service (no Eureka registration wait)
- Simpler architecture with K8s-native patterns
- Better health checking with liveness/readiness probes
- No redundant service registry to maintain

---

## Verification Commands

After migration, verify K8s service discovery works:

```bash
# Deploy to Minikube
kubectl apply -f k8s/

# Verify Services are registered
kubectl get svc

# Check DNS resolution
kubectl run -it --rm debug --image=busybox --restart=Never -- \
  nslookup base-station-service.default.svc.cluster.local

# Should return:
# Name: base-station-service.default.svc.cluster.local
# Address: 10.96.xxx.xxx (ClusterIP)

# Verify Gateway can discover services
kubectl logs -l app=api-gateway | grep "Discovered"

# Should show:
# Discovered services: [base-station-service, monitoring-service, ...]
```
